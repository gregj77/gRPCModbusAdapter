package com.gcs.gRPCModbusAdapter.serialPort

import com.gcs.gRPCModbusAdapter.config.SerialPortConfig
import gnu.io.PortInUseException
import gnu.io.RXTXPort
import gnu.io.SerialPortEvent
import gnu.io.SerialPortEventListener
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.scheduler.Scheduler
import reactor.util.retry.Retry
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

interface SerialPortDriver : Disposable {
    val name: String
    val isRunning: Boolean
    suspend fun communicate(data: ByteArray): ByteArray
}

class SerialPortDriverImpl(
    private val cfg: SerialPortConfig,
    private val scheduler: Scheduler,
    private val serialPortFactory: (String) -> RXTXPort,
    private val hardwareErrorPortCleaner: (String) -> Unit,
    commandHandlerFactory: CommandHandlerFactory
) : SerialPortDriver, HealthIndicator {
    private val logger = KotlinLogging.logger {}

    override val name: String
        get() = cfg.name
    override val isRunning: Boolean
        get() = running.get()

    private val id = AtomicInteger(0)
    private val running = AtomicBoolean(false)

    private val subscription: Disposable
    private var lastError: String? = null
    private lateinit var commandsChannel: SendChannel<CommandHandler.Command>

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


                scheduler.schedule {
                    runBlocking {
                        commandHandler.handleCommandLoop()
                    }
                }

                val commandHandlerToken = { commandHandler.close() }

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

    private fun onCleanup(commandHandlerToken: Disposable, serialPort: RXTXPort) {
        commandHandlerToken.dispose()
        running.set(false)
        with (serialPort) {
            removeEventListener()
            close()
        }
    }

    override suspend fun communicate(data: ByteArray) : ByteArray {
        val cmdId = id.incrementAndGet()

        if (isRunning.not()) {
            throw IllegalStateException("$name - failed to schedule command $cmdId - port not initialized!")
        }
        val resultChannel = Channel<CommandHandler.CommandResult>()
        commandsChannel.send(CommandHandler.Command(cmdId, data, resultChannel))
        val result = resultChannel.receive()
        result.error?.let {
            throw it
        }
        return result.result!!
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
private class RetryableException(msg: String) : Exception(msg)



