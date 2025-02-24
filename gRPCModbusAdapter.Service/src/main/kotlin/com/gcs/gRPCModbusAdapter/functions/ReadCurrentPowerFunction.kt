package com.gcs.gRPCModbusAdapter.functions

import com.gcs.gRPCModbusAdapter.functions.args.ReadCurrentPowerFunctionArgs
import com.gcs.gRPCModbusAdapter.functions.utils.MessageCRCServiceImpl
import com.gcs.gRPCModbusAdapter.functions.utils.toInt
import mu.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.scheduler.Scheduler

@Service
class ReadCurrentPowerFunction(crcService: MessageCRCServiceImpl, scheduler: Scheduler) : ModbusFunctionBase<ReadCurrentPowerFunctionArgs, Float>(crcService, 9, KotlinLogging.logger{ }, scheduler) {

    override val functionName: String
        get() = FunctionName

    override fun extractValue(response: ByteArray): Float = response.toInt(3).toFloat()

    companion object {
        const val FunctionName = "ReadCurrentPower"
    }
}
