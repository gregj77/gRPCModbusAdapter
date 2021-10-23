package com.gcs.gRPCModbusAdapter.functions

import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriver
import java.util.concurrent.CompletableFuture

interface ModbusFunction<TArgs, TResult> {
    val functionName: String

    fun execute(args: TArgs, serialPort: SerialPortDriver): CompletableFuture<TResult>
}