package com.gcs.gRPCModbusAdapter.functions

import com.gcs.gRPCModbusAdapter.functions.args.ReadCurrentPowerFunctionArgs
import com.gcs.gRPCModbusAdapter.functions.utils.MessageCRCService
import com.gcs.gRPCModbusAdapter.functions.utils.toInt
import org.springframework.stereotype.Service

@Service
class ReadCurrentPowerFunction(crcService: MessageCRCService) : ModbusFunction<ReadCurrentPowerFunctionArgs, Float>(crcService, 9) {

    override val functionName: String
        get() = "ReadCurrentPower"

    override fun extractValue(response: ByteArray): Float = response.toInt(3).toFloat() / 1000.0f
}