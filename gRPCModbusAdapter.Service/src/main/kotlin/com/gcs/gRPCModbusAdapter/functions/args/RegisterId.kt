package com.gcs.gRPCModbusAdapter.functions.args

enum class RegisterId(val value: Byte) {
    TOTAL_POWER(0x00),
    EXPORT_POWER(0x08),
    IMPORT_POWER(0x0A),
    CURRENT_POWER(0x86.toByte()),
    DEVICE_ID(0x15),
    CURRENT_VOLTAGE_PHASE1(0x80.toByte()),
    CURRENT_VOLTAGE_PHASE2(0x81.toByte()),
    CURRENT_VOLTAGE_PHASE3(0x82.toByte()),
}