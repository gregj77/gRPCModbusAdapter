package com.gcs.gRPCModbusAdapter.functions.args

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class ConstantsTest {


    @ParameterizedTest
    @MethodSource("validSettings")
    fun `only valid registerId's will resolve to TotalPower class`(registerId: RegisterId) {
        assertThat(Constants.checkRegisterBelongsToTotalPower(registerId)).isEqualTo(registerId)
    }

    @ParameterizedTest
    @MethodSource("nonPowerSettings")
    fun `all not-total power registerId's will throw error`(registerId: RegisterId) {
        assertThrows(IllegalArgumentException::class.java) { Constants.checkRegisterBelongsToTotalPower(registerId) }
    }

    companion object {
        @JvmStatic
        fun validSettings() = listOf(
            Arguments.of(RegisterId.TOTAL_POWER),
            Arguments.of(RegisterId.EXPORT_POWER),
            Arguments.of(RegisterId.IMPORT_POWER)
        )

        @JvmStatic
        fun nonPowerSettings() = listOf(
            Arguments.of(RegisterId.CURRENT_POWER)
        )
    }
}