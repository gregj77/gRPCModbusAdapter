package com.gcs.gRPCModbusAdapter.functions

import com.gcs.gRPCModbusAdapter.functions.args.ReadTotalPowerFunctionArgs
import com.gcs.gRPCModbusAdapter.functions.utils.MessageCRCService
import com.gcs.gRPCModbusAdapter.functions.utils.toInt
import org.springframework.stereotype.Service

@Service
class ReadTotalPowerFunction(crcService: MessageCRCService) : ModbusFunction<ReadTotalPowerFunctionArgs, Float>(crcService, 9) {

    override val functionName: String
        get() = "ReadTotalPower"

    override fun extractValue(response: ByteArray): Float = response.toInt(3).toFloat() / 100.0f
}