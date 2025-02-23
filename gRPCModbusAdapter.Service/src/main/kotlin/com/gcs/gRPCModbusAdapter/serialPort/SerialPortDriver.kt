package com.gcs.gRPCModbusAdapter.serialPort

import org.springframework.boot.actuate.health.HealthIndicator
import reactor.core.Disposable
import reactor.core.publisher.Flux
interface SerialPortDriver : Disposable, HealthIndicator {
    val name: String
    val isRunning: Boolean
    fun communicateAsync(data: ByteArray) : Flux<Byte>
}

internal class RetryableException(msg: String) : Exception(msg)
