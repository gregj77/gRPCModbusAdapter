package com.gcs.gRPCModbusAdapter.serialPort

import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortIOException
import com.fazecast.jSerialComm.SerialPortInvalidPortException
import com.fazecast.jSerialComm.SerialPortTimeoutException
import com.gcs.gRPCModbusAdapter.config.SerialPortConfig
import com.google.common.util.concurrent.Monitor
import io.micrometer.core.instrument.Counter
import mu.KotlinLogging
import org.springframework.boot.actuate.health.Health
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Sinks
import reactor.core.publisher.SynchronousSink
import reactor.core.scheduler.Scheduler
import reactor.util.retry.Retry
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis


class SerialPortDriverImplV2(
    private val cfg: SerialPortConfig,
    private val scheduler: Scheduler,
    private val serialPortFactory: (String) -> SerialPort,
    private val writtenBytesCounter: Counter,
    private val readBytesCounter: Counter,
) : SerialPortDriver {
    private val logger = KotlinLogging.logger {}
    private val requestsStream = Sinks.many().unicast().onBackpressureError<Triple<Int, ByteArray, FluxSink<Byte>>>()
    private val id = AtomicInteger(0)
    private val running = AtomicBoolean(false)

    private var lastError: String? = null
    private val subscription: Disposable
    private val lock: Monitor = Monitor()

    override val name: String
        get() = cfg.name

    override val isRunning: Boolean
        get() = running.get()

    init {
        subscription = Flux.create<Unit> { observer ->
            try {
                logger.debug { "initializing serial port [${scheduler.now(TimeUnit.SECONDS)}]" }
                val serialPort = serialPortFactory(cfg.name)
                logger.debug { "setting port parameters..." }

                logger.debug { "registering data listener..." }
                with(serialPort) {
                    setComPortParameters(cfg.baudRate, cfg.dataBits, cfg.stopBits.value, cfg.parity.value)
                    setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 250, 0)
                    if (!openPort()) {
                        logger.warn { "openPort returned false" }
                        observer.error(Exception("openPort returned false"))
                        return@create
                    }
                }

                val commandStreamToken = requestsStream
                    .asFlux()
                    .publishOn(scheduler)
                    .handle<Unit> { it, sink ->
                        processRequest(serialPort, it.first, it.second, it.third, sink)
                    }
                    .subscribe()

                observer.onCancel {
                    if (running.get()) {
                        logger.debug { "closing serial port due to cancel event" }
                        onCleanup(commandStreamToken, serialPort)
                    }
                }

                observer.onDispose {
                    if (running.get()) {
                        logger.debug { "closing serial port due to unsubscribe event" }
                        onCleanup(commandStreamToken, serialPort)
                    }
                }

                logger.debug { "all is initialized" }
                running.set(true)
                lastError = ""
                observer.next(Unit)

            } catch (err: SerialPortInvalidPortException) {
                lastError = "invalid serial port specification - ${err.message}"
                observer.error(RetryableException("$name - InvalidPort: ${err.message}"))
            }catch (err: SerialPortIOException) {
                lastError = "serial port IO exception - ${err.message}"
                observer.error(RetryableException("$name - I/O Exception: ${err.message}"))
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
        val cmdId = id.incrementAndGet()

        if (isRunning.not()) {
            return Flux.error(IllegalStateException("$name - failed to schedule command $cmdId - port not initialized!"))
        }

        return Flux.create<Byte?> {
            val cmd = Triple(cmdId, data, it)
            val result = requestsStream.tryEmitNext(cmd)
            if (result != Sinks.EmitResult.OK) {
                it.error(Exception("$name - failed to schedule command ${cmd.first} -> ${result.name}"))
            }
        }.delaySubscription(Duration.ofMillis(50L), scheduler)
    }

    override fun dispose() {
        logger.info { "Shutting down serial port service" }
        subscription.dispose()
    }


    private fun processRequest(
        port: SerialPort,
        cmdId: Int,
        bytes: ByteArray,
        responseOutStream: FluxSink<Byte>,
        processingComplete: SynchronousSink<Unit>
    ) {
        lock.enterInterruptibly()

        try {
            logger.debug { "$name - about to execute command $id... [${bytes.size} bytes]" }

            port.flushIOBuffers()

            val writtenBytes = port.writeBytes(bytes, bytes.size)
            writtenBytesCounter.increment(writtenBytes.toDouble())

            if (writtenBytes != bytes.size) {
                val msg = "$name - could not write all required bytes for command $cmdId"
                logger.warn { msg }
                responseOutStream.error(Exception(msg))
                return
            }

            val input = port.inputStream

            val timeTaken = measureTimeMillis {
                try {
                    var readBytes = 0
                    while (!responseOutStream.isCancelled) {
                        try {
                            val data = input.read()
                            if (data != -1) {
                                ++readBytes
                                readBytesCounter.increment()
                                responseOutStream.next(data.toByte())
                            } else {
                                responseOutStream.error(Exception("end of stream detected"))
                                break
                            }
                        } catch (e: SerialPortTimeoutException) {
                            logger.debug { "$name - timeout while waiting for response of $cmdId; read $readBytes so far..." }
                        }
                    }
                    logger.debug { "$name - caller for $cmdId cancelled data collection after collecting $readBytes bytes" }
                } catch (e: Exception) {
                    logger.warn { "$name - command $id failed to execute with ${e.message} <${e.javaClass.name}>" }
                    responseOutStream.error(e)
                }
            }
            logger.debug { "$name command $cmdId complete after $timeTaken millis" }
        } finally {
            lock.leave()
            processingComplete.next(Unit)
        }
    }

    private fun onCleanup(commandHandlerToken: Disposable, serialPort: SerialPort) {
        commandHandlerToken.dispose()
        running.set(false)
        with (serialPort) {
            removeDataListener()
            closePort()
        }
    }

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