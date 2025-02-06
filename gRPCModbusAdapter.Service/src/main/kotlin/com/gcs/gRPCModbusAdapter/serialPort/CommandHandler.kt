package com.gcs.gRPCModbusAdapter.serialPort

import com.gcs.gRPCModbusAdapter.async.ReadStream
import com.gcs.gRPCModbusAdapter.async.WriteStream
import com.gcs.gRPCModbusAdapter.config.SerialPortConfig
import io.micrometer.core.instrument.Counter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.io.InputStream
import java.io.OutputStream
import kotlin.system.measureTimeMillis

open class CommandHandlerFactory(private val cfg: SerialPortConfig, private val writeCounter: Counter, private val readCounter: Counter) {

    open fun createCommandHandler(input: InputStream, output: OutputStream): CommandHandler {
        return CommandHandler(input, output, cfg, writeCounter, readCounter)
    }
}

open class CommandHandler(
    input: InputStream,
    output: OutputStream,
    cfg: SerialPortConfig,
    writeCounter: Counter,
    readCounter: Counter) : AutoCloseable {

    private val logger = KotlinLogging.logger {  }
    private val name = cfg.name

    private val input = ReadStream(input, readCounter, cfg)
    private val output = WriteStream(output, writeCounter, cfg)

    private val commandChannel = Channel<Command>(128)

    val commands: SendChannel<Command>
        get() = commandChannel

    init {
        logger.info { "setup $name complete - wait for response: ${cfg.responseWaitTimeMillis} " }
    }

    fun notifyNewDataAvailable(newValue: Boolean, oldValue: Boolean) =
        input.notifyDataEvent(newValue, oldValue)


    suspend fun handleCommandLoop() = coroutineScope {
        commandChannel.receiveAsFlow().collect { cmd ->
            try {
                val result: ByteArray
                val time = measureTimeMillis {
                    input.drainBuffer()
                    output.writeData(cmd.data)
                    result = input.readData()
                }
                logger.debug { "$name - command ${cmd.id} request/reply completed in $time ms" }
                launch {
                    cmd.resultChannel.send(CommandResult(cmd.id, result, null))
                }
            } catch (err: Exception) {
                logger.warn { "$name - command ${cmd.id} failed to execute with ${err.message} <${err.javaClass.name}>" }
                launch {
                    cmd.resultChannel.send(CommandResult(cmd.id, null, err))
                }
            }
        }
    }

    class Command(val id: Int, val data: ByteArray, val resultChannel: Channel<CommandResult>)

    class CommandResult(val id: Int, val result: ByteArray?, val error: Exception?)

    override fun close() {
        commandChannel.close()
    }
}