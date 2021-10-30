package com.gcs.gRPCModbusAdapter.functions.args

import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriver
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class FunctionArgsTest {

    @ParameterizedTest
    @MethodSource("messageFieldUpdates")
    fun `all required message fields are guarded for valid entry`(mapping: Map<Int, Byte>) {
        val victim = DummyArgs(8) { mapping.forEach { (key, value) -> it[key] = value } }
        assertThrows<IllegalArgumentException> {  victim.toMessage(null) }
    }

    @Test
    fun `message must have at least 8 bytes`() {
        val victim = DummyArgs(7) { }
        assertThrows<IllegalArgumentException> {  victim.toMessage(null) }
    }

    @Test
    fun `for valid message callback is executed`() {
        val victim = DummyArgs(8) { it.fill(0x99.toByte(), 0, it.size) }
        var executed = false
        val result = victim.toMessage { executed = true }

        assertThat(executed).isTrue
        assertThat(result.count()).isEqualTo(8)
        assertThat(result.distinct()).hasSize(1).containsExactly(0x99.toByte())
    }

    companion object {
        @JvmStatic
        fun messageFieldUpdates() = listOf(
            Arguments.of(emptyMap<Int, Byte>()),
            Arguments.of(mapOf(1 to 0x11.toByte())),
            Arguments.of(mapOf(1 to 0x11.toByte(), 4 to 0x22.toByte())),
            Arguments.of(mapOf(1 to 0x11.toByte(), 4 to 0x22.toByte(), 5 to 0xff.toByte()))
        )
    }

}

class DummyArgs(msgSize: Int, val testCallback: (ByteArray) -> Unit) : FunctionArgs(mockk<SerialPortDriver>(), 1, RegisterId.TOTAL_POWER, msgSize) {
    override fun onFormatMessage(request: ByteArray) {
        testCallback(request)
    }
}