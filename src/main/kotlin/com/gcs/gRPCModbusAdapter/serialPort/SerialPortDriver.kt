package com.gcs.gRPCModbusAdapter.serialPort

import com.gcs.gRPCModbusAdapter.ModbusAdapter
import com.gcs.gRPCModbusAdapter.config.SerialPortConfig
import gnu.io.CommPortIdentifier
import gnu.io.PortInUseException
import gnu.io.SerialPortEvent
import io.reactivex.rxjava3.core.*
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject
import mu.KotlinLogging
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

interface SerialPortDriver : Disposable {
    val name: String
    val totalBytesRead: ULong
    val totalBytesWritten: ULong
    val isRunning: Boolean
    fun sendDataAndAwaitResponse(dataStream: Observable<ByteArray>) : Observable<Byte>
}


class SerialPortDriverImpl(val cfg: SerialPortConfig, val scheduler: Scheduler) : SerialPortDriver {
    private val logger = KotlinLogging.logger {}

    override val name: String
        get() = cfg.name
    override val totalBytesRead: ULong
        get() = totalRead
    override val totalBytesWritten: ULong
        get() = totalWrite
    override val isRunning: Boolean
        get() = running.get()

    private val id = AtomicInteger(0)
    private val running = AtomicBoolean(false)

    private var totalRead: ULong = 0u
    private var totalWrite: ULong = 0u

    private val requestStream = PublishSubject.create<Observable<CommunicationRequest>>()
    private val subscription: Disposable

    init {
        logger.info { "Initializing serial port driver with settings $cfg" }
        subscription = Observable.create<Observable<Byte>> { observer ->
            try {
                val portId = (CommPortIdentifier
                    .getPortIdentifiers()
                    .asSequence() as Sequence<CommPortIdentifier>)
                    .filter { it.portType == CommPortIdentifier.PORT_SERIAL && it.name == cfg.name}
                    .take(1)
                    .firstOrNull()

                if (portId == null) {
                    logger.error { "can't find serial port ${cfg.name} installed on the system" }
                    observer.onError(IllegalArgumentException("Port ${cfg.name} not found!"))
                } else {
                    logger.info { "opening port ${cfg.name}..." }
                    val serialPort = portId.open(ModbusAdapter::class.simpleName, 1_000)
                    logger.info { "setting port parameters..." }
                    serialPort.setSerialPortParams(cfg.baudRate, cfg.dataBits, cfg.stopBits.value, cfg.parity.value)

                    val inputByteStream = serialPort.inputStream
                    val outputByteStream = serialPort.outputStream

                    val activeDataEmitterRef: AtomicReference<Emitter<Byte>?> = AtomicReference(null)

                    val commandProcessingToken = requestStream
                        .flatMap { it }
                        .concatMap {
                            it.processCommunicationStream(activeDataEmitterRef, outputByteStream::write)
                        }
                        .observeOn(scheduler)
                        .subscribe()

                    logger.info { "registering data listener..." }

                    with (serialPort) {
                        addEventListener { onDataReceived(it, inputByteStream, activeDataEmitterRef, ByteArray(32)) }
                        notifyOnDataAvailable(true)
                        notifyOnCTS(true)
                        notifyOnDSR(true)
                        notifyOnRingIndicator(true)
                    }

                    running.set(true)

                    observer.setCancellable {
                        commandProcessingToken.dispose()
                        logger.info { "closing serial port due to unsubscribe event" }
                        running.set(false)
                        serialPort.removeEventListener()
                        serialPort.close()
                    }
                }

            } catch (err: Exception) {
                observer.onError(err)
            }
        }
            .retryWhen { errorStream ->
                return@retryWhen errorStream
                    .take(1)
                    .map { error ->
                        val canRetry = error is PortInUseException
                        logger.warn { "got error ${error.message} <${error.javaClass.name}> retry: $canRetry" }
                        return@map canRetry
                    }
                    .filter{ canRetry -> canRetry }
                    .flatMap { Observable.just(Unit).delay(5_000L, TimeUnit.SECONDS) }
            }
            .subscribe({}, { err -> logger.error { "cannot initialize serial port ${cfg.name} - ${err.message} <${err.javaClass.name}>" }})
    }


    override fun sendDataAndAwaitResponse(dataStream: Observable<ByteArray>): Observable<Byte> {
        return Observable.create<Byte?> { observer ->
            val cmdId = id.incrementAndGet()
            logger.debug { "received new command $cmdId" }
            val task = CommunicationRequest(cmdId, dataStream, observer)
            requestStream.onNext(Observable.just(task))
        }
            .subscribeOn(scheduler)
            .observeOn(scheduler)
    }

    override fun dispose() {
        logger.info { "Shutting down serial port service" }
        subscription.dispose()
    }

    override fun isDisposed(): Boolean = subscription.isDisposed

    private fun onDataReceived(
        args: SerialPortEvent,
        inputByteStream: InputStream,
        dataReadyCallbackReference: AtomicReference<Emitter<Byte>?>,
        byteArray: ByteArray
    ) {

        if (args.eventType == SerialPortEvent.DATA_AVAILABLE) {
            val dataReadyCallback = dataReadyCallbackReference.get()
            while (inputByteStream.available() > 0) {
                val read = inputByteStream.read(byteArray)
                totalRead += read.toULong()
                for (i in 0 until read) dataReadyCallback?.onNext(byteArray[i])
            }
        } else {
            logger.info { "got new event : ${args.eventType} - ${args.oldValue} -> ${args.newValue}" }
        }
    }
}

private data class CommunicationRequest(val id: Int, val inDataStream: Observable<ByteArray>, val outDataStream: ObservableEmitter<Byte>)  {

    fun processCommunicationStream(activeDataEmitterRef: AtomicReference<Emitter<Byte>?>, onSendData: (ByteArray) -> Unit ): ObservableSource<Unit> {
        logger.debug { "command $id - starting..." }
        activeDataEmitterRef.set(outDataStream)

        return ObservableSource { requestObserver ->

            val activeStreams = AtomicInteger(2)
            val cleanup = CompositeDisposable()

            val tryCloseStream = {
                if (activeStreams.decrementAndGet() == 0) {
                    logger.debug { "command $id - both up&down streams closed, releasing all resources..." }
                    requestObserver.onComplete()
                    cleanup.dispose()
                }
            }
            outDataStream.setCancellable(tryCloseStream)

            cleanup.add(inDataStream
                .map {
                    try {
                        logger.debug { "command $id - sending data package - ${it.size} bytes..." }
                        onSendData(it)
                    } catch (err: Exception) {
                        logger.error{ "command $id - error executing send ${err.message} <${err.javaClass.name}>"}
                        throw err
                    }
                }
                .subscribe({}, requestObserver::onError, tryCloseStream::invoke ))
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

