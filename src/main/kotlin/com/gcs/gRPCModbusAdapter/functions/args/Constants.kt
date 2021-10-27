package com.gcs.gRPCModbusAdapter.functions.args

internal object Constants {
    val READFUNCTION: Byte = 3

    fun checkRegisterBelongsToTotalPower(registerId: RegisterId): RegisterId {
        return when (registerId) {
            RegisterId.TOTAL_POWER -> registerId
            RegisterId.EXPORT_POWER -> registerId
            RegisterId.IMPORT_POWER -> registerId
            else -> throw IllegalArgumentException("invalid value $registerId for this function")
        }
    }
}