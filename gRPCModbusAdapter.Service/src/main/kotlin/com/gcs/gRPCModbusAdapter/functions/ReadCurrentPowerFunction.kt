package com.gcs.gRPCModbusAdapter.functions

import com.gcs.gRPCModbusAdapter.functions.args.ReadCurrentPowerFunctionArgs
import com.gcs.gRPCModbusAdapter.functions.utils.MessageCRCServiceImpl
import com.gcs.gRPCModbusAdapter.functions.utils.toInt
import org.springframework.stereotype.Service

@Service
class ReadCurrentPowerFunction(crcService: MessageCRCServiceImpl) : ModbusFunctionBase<ReadCurrentPowerFunctionArgs, Float>(crcService, 9) {

    override val functionName: String
        get() = FunctionName

    override fun extractValue(response: ByteArray): Float = response.toInt(3).toFloat() / 1000.0f

    companion object {
        const val FunctionName = "ReadCurrentPower"
    }
}

