package com.gcs.gRPCModbusAdapter.serialPort

import com.gcs.gRPCModbusAdapter.config.SerialPortConfig
import io.micrometer.core.instrument.Counter
import mu.KotlinLogging
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.measureTimeMillis

open class CommandHandlerFactory(private val cfg: SerialPortConfig, private val writeCounter: Counter, private val readCounter: Counter) {

    open fun createCommandHandler(input: InputStream, output: OutputStream): CommandHandler {
        return CommandHandler(input, output, cfg, writeCounter, readCounter)
    }
}

open class CommandHandler(
    private val input: InputStream,
    private val output: OutputStream,
    private val cfg: SerialPortConfig,
    private val writeCounter: Counter,
    private val readCounter: Counter) {

    private val logger = KotlinLogging.logger {  }
    protected val dataReady = AtomicBoolean(false)
    private val receiveBuffer = ByteArray(128)
    private val sleepBetweenBytes = ((((cfg.dataBits + cfg.stopBits.value + 2) / cfg.baudRate.toDouble()) * 1_000.0) + 1.0).toLong()
    private val name = cfg.name

    fun notifyNewDataAvailable() {
        dataReady.set(true)
    }

    fun onHandleCommand(id: Int, data: ByteArray, resultHandler: CompletableFuture<List<Byte>>) {
        logger.debug { "$name - about to execute command $id..." }
        try {

            drainBuffer()
            output.write(data)
            writeCounter.increment(data.size.toDouble())
            awaitCommandResponse()

            if (dataReady.get()) {
                var readData = 0
                var loops = 5
                while (--loops >= 0) {
                    if (input.available() > 0) {
                        val chunkSize = input.read(receiveBuffer, readData, receiveBuffer.size - readData)
                        readData += chunkSize
                        readCounter.increment(chunkSize.toDouble())
                    }
                    tick()
                }
                logger.debug { "$name - command $id - request/reply completed with $readData bytes" }
                dataReady.set(false)
                resultHandler.complete(receiveBuffer.take(readData).toList())
            } else {
                logger.warn { "$name - command $id - timeout while waiting for data!"}
                resultHandler.completeExceptionally(TimeoutException("$name - did not receive any data in ${cfg.responseWaitTimeMillis.toLong()} ms"))
            }

        } catch (err: Exception) {
            logger.warn { "$name - command $id failed to execute with ${err.message} <${err.javaClass.name}>" }
            resultHandler.completeExceptionally(err)
        }
    }

    protected open fun drainBuffer() {
        while (input.available() > 0) {
            input.read(receiveBuffer)
        }
    }

    protected open fun awaitCommandResponse() {
        var remainingWaitTime = cfg.responseWaitTimeMillis.toLong()
        while (remainingWaitTime >= 0 && !dataReady.get()) {
            remainingWaitTime -= measureTimeMillis {
                Thread.sleep(sleepBetweenBytes)
            }
        }
    }

    protected open fun tick() {
        Thread.sleep(sleepBetweenBytes)
    }
}