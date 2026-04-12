package com.gcs.gRPCModbusAdapter.functions

import com.gcs.gRPCModbusAdapter.functions.args.WriteEnergyBalanceRuleArgs
import com.gcs.gRPCModbusAdapter.functions.utils.MessageCRCServiceImpl
import mu.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.scheduler.Scheduler

@Service
class NotifyWaterTankWithEnergyBalanceFunction(crcService: MessageCRCServiceImpl, scheduler: Scheduler)
: ModbusFunctionBase<WriteEnergyBalanceRuleArgs, Boolean>(crcService, 8, KotlinLogging.logger{ }, scheduler) {
    override fun extractValue(response: ByteArray): Boolean {
        return response[1] == 0x10.toByte();
    }

    override val functionName: String
        get() = FunctionName

    companion object {
        const val FunctionName = "NotifyWaterTankWithEnergyBalance"
    }
}