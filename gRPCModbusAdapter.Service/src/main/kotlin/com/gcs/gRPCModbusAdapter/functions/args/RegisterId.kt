package com.gcs.gRPCModbusAdapter.functions.args

enum class RegisterId(val value: Byte) {
    TOTAL_POWER(0x00),
    EXPORT_POWER(0x08),
    IMPORT_POWER(0x0A),
    CURRENT_POWER(0x86.toByte()),
    DEVICE_ID(0x15);
}