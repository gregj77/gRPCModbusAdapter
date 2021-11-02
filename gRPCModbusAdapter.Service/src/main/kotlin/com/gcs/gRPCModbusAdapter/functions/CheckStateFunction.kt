package com.gcs.gRPCModbusAdapter.functions

import com.gcs.gRPCModbusAdapter.functions.args.CheckStateFunctionArgs
import com.gcs.gRPCModbusAdapter.functions.utils.MessageCRCService
import org.springframework.stereotype.Service

@Service
class CheckStateFunction(crcService: MessageCRCService) : ModbusFunctionBase<CheckStateFunctionArgs, Byte>(crcService, 9) {
    override val functionName: String
        get() = FunctionName

    override fun extractValue(response: ByteArray): Byte = response[4]

    companion object {
        const val FunctionName = "ReadModbusDeviceId"
    }
}