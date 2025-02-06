package com.gcs.gRPCModbusAdapter.async

import com.gcs.gRPCModbusAdapter.config.SerialPortConfig
import io.micrometer.core.instrument.Counter
import kotlinx.coroutines.delay
import mu.KotlinLogging
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger {  }

class WriteStream(private val outputStream: OutputStream, private val writeCounter: Counter, private val cfg: SerialPortConfig) {

    suspend fun writeData(bytes: ByteArray) =
        suspendCoroutine<Int> {
            try {
                outputStream.write(bytes)
                writeCounter.increment(bytes.size.toDouble())
                it.resume(bytes.size)
            } catch (err: Exception) {
                it.resumeWithException(err)
            }
        }
}

class ReadStream(private val inputStream: InputStream, private val readCounter: Counter, private val cfg: SerialPortConfig) {
    private val name: String = cfg.name
    private val dataReady = AtomicBoolean(false)
    private val receiveBuffer = ByteArray(128)
    private val sleepBetweenBytes = ((((cfg.dataBits + cfg.stopBits.value + 2) / cfg.baudRate.toDouble()) * 1_000.0) + 1.0).toLong()

    fun notifyDataEvent(newValue: Boolean, oldValue: Boolean) {
        if (newValue != oldValue) {
            logger.debug { "$name - data available event new: $newValue, old: $oldValue" }
        }
        dataReady.set(newValue)
    }

    suspend fun readData(): ByteArray {
        waitForData()

        if (dataReady.get()) {
            var readData = 0
            var loops = 10
            while (--loops >= 0) {
                suspendCoroutine<Unit> {
                    try {
                        if (inputStream.available() > 0) {
                            val chunkSize = inputStream.read(receiveBuffer, readData, receiveBuffer.size - readData)
                            logger.debug { "$name - command got chunk $chunkSize" }
                            readData += chunkSize
                            readCounter.increment(chunkSize.toDouble())
                            loops = 10
                        }
                        it.resume(Unit)
                    } catch (err: Exception) {
                        it.resumeWithException(err)
                    }
                }
                delay(sleepBetweenBytes)
            }
            dataReady.set(false)
            return receiveBuffer.take(readData).toByteArray()
        }

        throw TimeoutException("$name - did not receive any data in ${cfg.responseWaitTimeMillis.toLong()} ms")
    }

    suspend fun drainBuffer() = suspendCoroutine<Unit> {
        try {
            while (inputStream.available() > 0) {
                inputStream.read(receiveBuffer)
            }
            dataReady.set(false)
            it.resume(Unit)
        } catch (err: Exception) {
            it.resumeWithException(err)
        }
    }

    private suspend fun waitForData(): Long = measureTimeMillis {
        var remainingWaitTime = cfg.responseWaitTimeMillis.toLong()
        while (remainingWaitTime >= 0 && !dataReady.get()) {
            delay(sleepBetweenBytes)
            remainingWaitTime -= sleepBetweenBytes
        }
    }
}