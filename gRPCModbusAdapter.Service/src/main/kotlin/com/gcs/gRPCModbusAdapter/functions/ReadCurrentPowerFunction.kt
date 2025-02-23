package com.gcs.gRPCModbusAdapter.functions

import com.gcs.gRPCModbusAdapter.functions.args.ReadCurrentPowerFunctionArgs
import com.gcs.gRPCModbusAdapter.functions.utils.MessageCRCServiceImpl
import com.gcs.gRPCModbusAdapter.functions.utils.toInt
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
class ReadCurrentPowerFunction(crcService: MessageCRCServiceImpl) : ModbusFunctionBase<ReadCurrentPowerFunctionArgs, Float>(crcService, 9, KotlinLogging.logger{ }) {

    override val functionName: String
        get() = FunctionName

    override fun extractValue(response: ByteArray): Float = response.toInt(3).toFloat()

    companion object {
        const val FunctionName = "ReadCurrentPower"
    }
}

