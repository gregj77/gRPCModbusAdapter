package com.gcs.gRPCModbusAdapter.functions

import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriver
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture

@Service
class ReadCurrentPowerFunction : ModbusFunction<Int, Int> {
    override val functionName: String
        get() = "ReadCurrentPower"

    override fun execute(args: Int, serialPort: SerialPortDriver): CompletableFuture<Int> {
        TODO("Not yet implemented")
    }
}