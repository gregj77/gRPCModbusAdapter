package com.gcs.gRPCModbusAdapter.functions

import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriver
import java.util.concurrent.CompletableFuture

interface ModbusFunction<in TArgs : FunctionArgs, TResult> {
    val functionName: String

    fun execute(args: TArgs): CompletableFuture<TResult>
}

open class FunctionArgs(val driver: SerialPortDriver, val deviceId: Byte)

open class ReadPowerFunctionArgs(driver: SerialPortDriver,deviceId: Byte, val registerId: RegisterId): FunctionArgs(driver, deviceId)

enum class RegisterId(val value: Byte) {
    TOTAL(0x00),
    EXPORT(0x08),
    IMPORT(0x0A);
}