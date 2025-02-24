package com.gcs.gRPCModbusAdapter.functions

import com.gcs.gRPCModbusAdapter.functions.args.ReadCurrentVoltageFunctionArgs
import com.gcs.gRPCModbusAdapter.functions.utils.MessageCRCServiceImpl
import com.gcs.gRPCModbusAdapter.functions.utils.toShort
import mu.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.scheduler.Scheduler

@Service
class ReadCurrentVoltageFunction(crcService: MessageCRCServiceImpl, scheduler: Scheduler) : ModbusFunctionBase<ReadCurrentVoltageFunctionArgs, Float>(crcService, 9, KotlinLogging.logger{ }, scheduler) {

    override val functionName: String
        get() = FunctionName

    override fun extractValue(response: ByteArray): Float = response.toShort(3) / 10.0f

    companion object {
        const val FunctionName = "ReadCurrentVoltage"
    }
}
