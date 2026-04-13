package com.gcs.gRPCModbusAdapter.functions.watertank

import kotlinx.serialization.Serializable


@Serializable
enum class TempStatus {
    LOW, MEDIUM, HIGH, ERROR, UNKNOWN
}

@Serializable
data class TankReading(
    val temperature: Double,
    val status: TempStatus
)

@Serializable
data class BoilerStatus(
    val outputTemperature: Double,
    val isRelayOn: Boolean
)

@Serializable
enum class RuleType(val bit: Int) {
    MIN_TEMPERATURE(1),
    HIGH_PRODUCTION_YIELD(2),
    HOT_WATER_BOOST(4),
    LEGIONELLA(8),
    LOW_TEMPERATURE_BOOST(16),
    HEAT_PUMP_CONTROL(0x4000),
    BOILER_CONTROL(0x8000);

    companion object {
        fun fromMask(rulesMask: Int): List<RuleStatus> {
            return RuleType.values().map { rule ->
                RuleStatus(
                    id = rule.bit,
                    name = rule.toString(),
                    isActive = (rulesMask and rule.bit) != 0
                )
            }
        }
    }
}

@Serializable
data class RuleStatus(
    val id: Int,
    val name: String,
    val isActive: Boolean
)

@Serializable
data class WaterTankStatus(
    val top: TankReading,
    val bottom: TankReading,
    val boiler: BoilerStatus,
    val rules: List<RuleStatus>
)

fun Iterable<RuleType>.toMask(): Int {
    return this.fold(0) { acc, rule -> acc or rule.bit }
}

fun Iterable<RuleType>.toWaterTankPayload(): ByteArray {
    val ruleMask = this.toMask()
    return byteArrayOf(
        0x00, 0x01,                   // Rejestr 1000: Wartość 1
        0x00, 0x00,                   // Rejestr 1001: Wartość 0
        (ruleMask shr 8).toByte(),    // Rejestr 1002: Maska (High Byte)
        (ruleMask and 0xFF).toByte()  // Rejestr 1002: Maska (Low Byte)
    )
}

fun Int.toEnergyBalancePayload(): ByteArray {
    return byteArrayOf(
        0x00, 0x66, // Rejestr 1000: wartosc 102
        (this shr 24).toByte(),
        (this shr 16).toByte(),
        (this shr 8).toByte(),
        (this and 0xFF).toByte()
    )
}