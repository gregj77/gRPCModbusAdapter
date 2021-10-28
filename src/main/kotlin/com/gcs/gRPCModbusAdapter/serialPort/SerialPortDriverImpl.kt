package com.gcs.gRPCModbusAdapter.serialPort

import com.gcs.gRPCModbusAdapter.config.SerialPortConfig
import gnu.io.PortInUseException
import gnu.io.RXTXPort
import gnu.io.SerialPortEvent
import gnu.io.SerialPortEventListener
import io.micrometer.core.instrument.Counter
import io.reactivex.rxjava3.core.*
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject
import mu.KotlinLogging
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthContributor
import org.springframework.boot.actuate.health.HealthIndicator
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
    fun establishStream(dataStream: Observable<ByteArray>) : Observable<Byte>
}


class SerialPortDriverImpl(
    private val cfg: SerialPortConfig,
    private val scheduler: Scheduler,
    serialPortFactory: (String) -> RXTXPort,
    private val writeCounter: Counter,
    private val readCounter: Counter
) : SerialPortDriver, HealthIndicator, HealthContributor {
    private val logger = KotlinLogging.logger {}

    override val name: String
        get() = cfg.name
    override val totalBytesRead: ULong
        get() = readCounter.count().toULong()
    override val totalBytesWritten: ULong
        get() = writeCounter.count().toULong()
    override val isRunning: Boolean
        get() = running.get()

    private val id = AtomicInteger(0)
    private val running = AtomicBoolean(false)


    private val requestStream = PublishSubject.create<Observable<CommunicationRequest>>()
    private val subscription: Disposable
    private var lastError: String? = null

    init {
        logger.info { "Initializing serial port driver with settings $cfg" }
        val initialSubscription = requestStream
            .flatMap { it }
            .subscribeOn(scheduler)
            .observeOn(scheduler)
            .subscribe {
                it.outDataStream.onError(IllegalStateException("port ${cfg.name} is not yet initialized!"))
            }
        subscription = Observable.create<Observable<Byte>> { observer ->
            try {
                val serialPort = serialPortFactory(cfg.name)
                logger.info { "setting port parameters..." }
                serialPort.setSerialPortParams(cfg.baudRate, cfg.dataBits, cfg.stopBits.value, cfg.parity.value)

                val inputByteStream = serialPort.inputStream
                val outputByteStream = serialPort.outputStream

                val activeDataEmitterRef: AtomicReference<Emitter<Byte>?> = AtomicReference(null)

                val commandProcessingToken = requestStream
                    .flatMap { it }
                    .concatMap {
                        it.processCommunicationStream(activeDataEmitterRef) { bytes ->
                            writeCounter.increment(bytes.size.toDouble())
                            outputByteStream.write(bytes)
                        }
                    }
                    .observeOn(scheduler)
                    .subscribe()

                logger.info { "registering data listener..." }

                val onDataReceivedCallbackHandler = SerialPortEventListener {
                    onDataReceived(it, inputByteStream, activeDataEmitterRef, ByteArray(32))
                }

                with (serialPort) {
                    addEventListener(onDataReceivedCallbackHandler)
                    notifyOnDataAvailable(true)
                }

                running.set(true)

                observer.setCancellable {
                    commandProcessingToken.dispose()
                    logger.info { "closing serial port due to unsubscribe event" }
                    running.set(false)
                    serialPort.removeEventListener()
                    serialPort.close()
                }

                logger.debug { "all is initialized - disposing initial subscription" }
                initialSubscription.dispose()

            } catch (err: Exception) {
                observer.onError(err)
            }
        }
            .retryWhen { errorStream ->
                return@retryWhen errorStream
                    .flatMap {
                        error ->
                        val canRetry = error is PortInUseException
                        logger.warn { "got error ${error.message} <${error.javaClass.name}> retry: $canRetry" }
                        if (canRetry)  Observable.just(1).delay(5_000L, TimeUnit.MILLISECONDS, scheduler)
                        else Observable.error(error)
                    }
            }
            .subscribe(
                {},
                { err ->
                    lastError = "cannot initialize serial port ${cfg.name} - ${err.message} <${err.javaClass.name}>"
                    logger.error { lastError }
                })
    }


    override fun establishStream(dataStream: Observable<ByteArray>): Observable<Byte> {
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
        running.set(false)
    }

    override fun isDisposed(): Boolean = subscription.isDisposed

    override fun health(): Health {
        return if (isRunning) {
            Health.up().build()
        } else if (lastError != null) {
            if (subscription.isDisposed) {
                Health.down().withDetail("lastError", lastError).build()
            } else {
                Health.outOfService().withDetail("lastError", lastError).build()
            }
        } else {
            Health.down().withDetail("status", "already disposed").build()
        }
    }

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
                readCounter.increment(read.toDouble())
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
                    activeDataEmitterRef.set(null)
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

