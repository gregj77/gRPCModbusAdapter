package com.gcs.gRPCModbusAdapter.serialPort

import com.gcs.gRPCModbusAdapter.config.SerialPortConfig
import gnu.io.PortInUseException
import gnu.io.RXTXPort
import gnu.io.SerialPortEvent
import gnu.io.SerialPortEventListener
import mu.KotlinLogging
import org.springframework.boot.actuate.health.Health
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Scheduler
import reactor.util.retry.Retry
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class SerialPortDriverImpl(
    private val cfg: SerialPortConfig,
    private val scheduler: Scheduler,
    private val serialPortFactory: (String) -> RXTXPort,
    private val hardwareErrorPortCleaner: (String) -> Unit,
    commandHandlerFactory: CommandHandlerFactory
) : SerialPortDriver {
    private val logger = KotlinLogging.logger {}

    override val name: String
        get() = cfg.name
    override val isRunning: Boolean
        get() = running.get()

    private val id = AtomicInteger(0)
    private val running = AtomicBoolean(false)

    private val subscription: Disposable
    private val commandsStream = Sinks.many().unicast().onBackpressureBuffer<Triple<Int, ByteArray, CompletableFuture<List<Byte>>>>()
    private var lastError: String? = null

    init {

        subscription = Flux.create<Unit> { observer ->
            try {
                logger.debug { "initializing serial port [${scheduler.now(TimeUnit.SECONDS)}]" }
                val serialPort = serialPortFactory(cfg.name)
                logger.debug { "setting port parameters..." }
                serialPort.setSerialPortParams(cfg.baudRate, cfg.dataBits, cfg.stopBits.value, cfg.parity.value)

                val inputByteStream = serialPort.inputStream
                val outputByteStream = serialPort.outputStream

                logger.debug { "registering data listener..." }

                val commandHandler = commandHandlerFactory.createCommandHandler(inputByteStream, outputByteStream)

                val onDataReceivedCallbackHandler = SerialPortEventListener {
                    when (it.eventType) {
                        SerialPortEvent.HARDWARE_ERROR -> { lastError = "hardware error"; observer.error(RetryableException("HardwareError")) }
                        SerialPortEvent.DATA_AVAILABLE -> commandHandler.notifyNewDataAvailable(it.newValue, it.oldValue)
                        else -> logger.info { "$name -> not supported event type ${it.eventType} received. ignoring..." }
                    }
                }

                with(serialPort) {
                    addEventListener(onDataReceivedCallbackHandler)
                    notifyOnDataAvailable(true)
                }

                val commandHandlerToken = initializeRequestStream(commandHandler, commandsStream.asFlux())

                observer.onCancel {
                    if (running.get()) {
                        logger.debug { "closing serial port due to cancel event" }
                        onCleanup(commandHandlerToken, serialPort)
                        hardwareErrorPortCleaner.invoke(name)
                    }
                }

                observer.onDispose {
                    if (running.get()) {
                        logger.debug { "closing serial port due to unsubscribe event" }
                        onCleanup(commandHandlerToken, serialPort)
                    }
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

    private fun initializeRequestStream(
        handler: CommandHandler,
        requests: Flux<Triple<Int, ByteArray, CompletableFuture<List<Byte>>>>): Disposable {

        return requests
            .publishOn(scheduler)
            .subscribe {
                val (id, data, resultHandler) = it
                handler.onHandleCommand(id, data, resultHandler)
            }
    }

    private fun onCleanup(commandHandlerToken: Disposable, serialPort: RXTXPort) {
        commandHandlerToken.dispose()
        running.set(false)
        with (serialPort) {
            removeEventListener()
            close()
        }
    }

    override fun communicateAsync(data: ByteArray): Flux<Byte> {
        val cmdId = id.incrementAndGet()

        if (isRunning.not()) {
            return Flux.error(IllegalStateException("$name - failed to schedule command $cmdId - port not initialized!"))
        }

        val result = CompletableFuture<List<Byte>>()
        val cmd = Triple(cmdId, data, result)
        val emitResult = commandsStream.tryEmitNext(cmd)
        return if (emitResult != Sinks.EmitResult.OK) {
            Flux.error(Exception("$name - failed to schedule command ${cmd.first} -> ${emitResult.name}"))
        } else {
            Flux.create {
                result.handle { result, error ->
                    if (error != null) {
                        it.error(error)
                    } else {
                        result.forEach(it::next)
                        it.complete()
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
}



