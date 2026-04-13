package com.gcs.gRPCModbusAdapter.functions

import com.gcs.gRPCModbusAdapter.functions.args.ReadWaterTankFunctionArgs
import com.gcs.gRPCModbusAdapter.functions.utils.MessageCRCService
import com.gcs.gRPCModbusAdapter.functions.watertank.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.scheduler.Scheduler
@Service
class ReadWaterTankStatus(crcService: MessageCRCService, scheduler: Scheduler) : ModbusFunctionBase<ReadWaterTankFunctionArgs, String>(crcService, 13, KotlinLogging.logger{ }, scheduler) {

    override val functionName: String
        get() = FunctionName

    override fun extractValue(response: ByteArray): String  {
        // register 1 (16 bits)
        val tankTopRaw = readS16(response, 3)
        val tankTopReading = unpackModbusRegister(tankTopRaw)

        // Register 2 (16 bits)
        val tankBottomRaw = readS16 (response, 5)
        val tankBottomReading = unpackModbusRegister(tankBottomRaw)

        // Register 3 (16 bits)
        val boilerControlStatusRaw = readS16(response, 7)
        val boilerStatus = unpackBoilerData(boilerControlStatusRaw)

        // Register 4 (00 33) -> Your bitmask (51 decimal)
        val rulesMask = readS16(response, 9)
        val allRules = RuleType.fromMask(rulesMask)

        val result = WaterTankStatus(
            top = tankTopReading,
            bottom = tankBottomReading,
            boiler = boilerStatus,
            rules = allRules
        )

        return Json.encodeToString(result)
    }

    private fun readS16(data: ByteArray, startIndex: Int): Int {
        return ((data[startIndex].toInt() and 0xFF) shl 8) or (data[startIndex + 1].toInt() and 0xFF)
    }

    fun unpackModbusRegister(registerValue: Int): TankReading {
        // 1. Extract the Status (The 10,000s digit)
        val statusCode = registerValue / 10000

        // 2. Extract the Temperature part (The remainder)
        // We mask with 0x03FF as per your C++ code to be safe
        val rawTemp = (registerValue % 10000) and 0x03FF

        val status = when (statusCode) {
            1 -> TempStatus.LOW
            2 -> TempStatus.MEDIUM
            3 -> TempStatus.HIGH
            5 -> TempStatus.ERROR
            else -> TempStatus.UNKNOWN
        }

        return TankReading(
            temperature = rawTemp / 10.0,
            status = status
        )
    }

    private fun unpackBoilerData(registerValue: Int): BoilerStatus {
        val relayOn = (registerValue / 10000) == 1

        val rawTemp = (registerValue % 10000) and 0x03FF

        return BoilerStatus(
            outputTemperature = rawTemp / 10.0,
            isRelayOn = relayOn
        )
    }

    companion object {
        const val FunctionName = "ReadWaterTankStatus"
    }
}
