package com.gcs.gRPCModbusAdapter.functions

import com.gcs.gRPCModbusAdapter.functions.args.CheckStateFunctionArgs
import com.gcs.gRPCModbusAdapter.functions.utils.MessageCRCService
import mu.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.scheduler.Scheduler

@Service
class CheckStateFunction(crcService: MessageCRCService, scheduler: Scheduler) : ModbusFunctionBase<CheckStateFunctionArgs, Byte>(crcService, 9, KotlinLogging.logger{ }, scheduler) {

    override val functionName: String
        get() = FunctionName

    override fun extractValue(response: ByteArray): Byte = response[0]

    companion object {
        const val FunctionName = "ReadModbusDeviceId"
    }
}
