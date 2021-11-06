package com.gcs.gRPCModbusAdapter.serialPort

import com.gcs.gRPCModbusAdapter.config.SerialPortConfig
import gnu.io.PortInUseException
import gnu.io.RXTXPort
import gnu.io.SerialPortEvent
import gnu.io.SerialPortEventListener
import io.micrometer.core.instrument.Counter
import mu.KotlinLogging
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.scheduler.Scheduler
import reactor.util.retry.Retry
import java.io.InputStream
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

interface SerialPortDriver : Disposable {
    val name: String
    val totalBytesRead: ULong
    val totalBytesWritten: ULong
    val isRunning: Boolean
    fun communicateAsync(data: ByteArray) : Flux<Byte>
}

class SerialPortDriverImpl(
    private val cfg: SerialPortConfig,
    private val scheduler: Scheduler,
    private val serialPortFactory: (String) -> RXTXPort,
    private val hardwareErrorPortCleaner: (String) -> Unit,
    private val writeCounter: Counter,
    private val readCounter: Counter
) : SerialPortDriver, HealthIndicator {
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
    private val commandsInProgress = AtomicInteger(0)
    private val commands = ConcurrentLinkedQueue<CommandRequest>()

    private val subscription: Disposable
    private var lastError: String? = null
    private var commandHandler: ((ByteArray, FluxSink<Byte>) -> Disposable)? = null

    init {

        subscription = Flux.create<Unit> { observer ->
            try {
                logger.debug { "initializing serial port [${scheduler.now(TimeUnit.SECONDS)}]" }
                val serialPort = serialPortFactory(cfg.name)
                logger.debug { "setting port parameters..." }
                serialPort.setSerialPortParams(cfg.baudRate, cfg.dataBits, cfg.stopBits.value, cfg.parity.value)

                val inputByteStream = serialPort.inputStream
                val outputByteStream = serialPort.outputStream
                val activeDataEmitterRef: AtomicReference<FluxSink<Byte>?> = AtomicReference(null)

                logger.debug { "registering data listener..." }

                val receiveBuffer = ByteArray(32)
                val onDataReceivedCallbackHandler = SerialPortEventListener {
                    when (it.eventType) {
                        SerialPortEvent.HARDWARE_ERROR -> { lastError = "hardware error"; observer.error(RetryableException("HardwareError")) }
                        SerialPortEvent.DATA_AVAILABLE -> onDataReceived(inputByteStream, activeDataEmitterRef, receiveBuffer)
                        else -> logger.info { "$name -> not supported event type ${it.eventType} received. ignoring..." }
                    }
                }

                commandHandler = { sendBuffer, responseHandler  ->
                    activeDataEmitterRef.set(responseHandler)
                    outputByteStream.write(sendBuffer)
                    writeCounter.increment(sendBuffer.size.toDouble())
                    Disposable { activeDataEmitterRef.set(null) }
                }

                with(serialPort) {
                    addEventListener(onDataReceivedCallbackHandler)
                    notifyOnDataAvailable(true)
                }

                observer.onCancel {
                    if (running.get()) {
                        logger.debug { "closing serial port due to cancel event" }
                        running.set(false)
                        serialPort.removeEventListener()
                        serialPort.close()
                        hardwareErrorPortCleaner.invoke(name)
                    }
                }

                observer.onDispose {
                    if (running.get()) {
                        logger.debug { "closing serial port due to unsubscribe event" }
                        running.set(false)
                        serialPort.removeEventListener()
                        serialPort.close()
                    }
                    commandHandler = null
                }

                logger.debug { "all is initialized" }
                running.set(true)
                lastError = ""
                observer.next(Unit)

            } catch (err: PortInUseException) {
                lastError = "serial port is in use - ${err.message}"
                observer.error(RetryableException("$name - PortInUse: ${err.message}"))
            } catch (err: Exception) {
                lastError = "unhandled error - ${err.message} <${err.javaClass.name}>"
                observer.error(RetryableException("$name - ${err.message} <${err.javaClass.name}>"))
            }
        }
            .retryWhen(
                Retry.fixedDelay(Long.MAX_VALUE, Duration.ofSeconds(5L)).scheduler(scheduler))
            .subscribe(
                { logger.info { "serial port $name is up and running with settings $cfg" } },
                { err ->
                    lastError = "cannot initialize serial port ${cfg.name} - ${err.message} <${err.javaClass.name}>"
                    logger.error { lastError }
                })
    }


    override fun communicateAsync(data: ByteArray): Flux<Byte> {
        return Flux
            .create<Byte?> { consumer ->
                val cmdId = id.incrementAndGet()
                logger.debug { "received new command $cmdId" }
                val cmd = CommandRequest(cmdId, data, consumer)
                commands.offer(cmd)

                consumer.onDispose {
                    cmd.dispose()
                    scheduler.schedule({
                        val leftCommands = commandsInProgress.decrementAndGet()
                        logger.debug { "command $cmdId completed; commands left: $leftCommands" }
                        if (leftCommands != 0) {
                            processCommandQueue()
                        }
                    }, 31, TimeUnit.MILLISECONDS)
                }

                if (commandsInProgress.incrementAndGet() == 1) {
                    scheduler.schedule(this::processCommandQueue)
                }
            }
            .subscribeOn(scheduler)
            .publishOn(scheduler)
    }

    private fun processCommandQueue() {
        val command = commands.poll()
        if (null != command) {
            when (commandHandler) {
                null -> {
                    logger.warn { "$name - trying to execute command on not initialized port" }
                    command.responseConsumer.error(IllegalStateException("$name port is not initialized"))
                }
                else -> {
                    try {
                        logger.debug { "$name - about to execute command ${command.id}" }
                        command.cleanup = commandHandler!!.invoke(command.request, command.responseConsumer)
                    } catch (err: Exception) {
                        logger.warn { "$name - command ${command.id} failed to execute with ${err.message} <${err.javaClass.name}>" }
                        command.responseConsumer.error(err)
                    }
                }
            }
        }
    }

    override fun dispose() {
        logger.info { "Shutting down serial port service" }
        subscription.dispose()
    }

    override fun isDisposed(): Boolean = subscription.isDisposed

    override fun health(): Health {
        return if (isRunning) {
            Health.up().build()
        } else if (subscription.isDisposed) {
            Health.down().withDetail("status", "already disposed").build()
        } else {
            Health.outOfService().withDetail("lastError", lastError).build()
        }
    }

    private fun onDataReceived(
        inputByteStream: InputStream,
        dataReadyCallbackReference: AtomicReference<FluxSink<Byte>?>,
        byteArray: ByteArray
    ) {
        val dataReadyCallback = dataReadyCallbackReference.get()
        while (inputByteStream.available() > 0) {
            val read = inputByteStream.read(byteArray)
            readCounter.increment(read.toDouble())
            for (i in 0 until read) dataReadyCallback?.next(byteArray[i])
        }
    }
}

private class CommandRequest(val id: Int, val request: ByteArray, val responseConsumer: FluxSink<Byte>, var cleanup: Disposable? = null) : Disposable {
    override fun dispose() {
        cleanup?.dispose()
    }
}

private class RetryableException(msg: String) : Exception(msg)

