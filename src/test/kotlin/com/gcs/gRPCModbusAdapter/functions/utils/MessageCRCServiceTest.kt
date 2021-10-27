package com.gcs.gRPCModbusAdapter.functions.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class MessageCRCServiceTest {

    @Test
    fun `calculate CRC for well known message generates valid CRC`() {
        val bytes = byteArrayOf( 0x00, 0x10, 0x00, 0x1A, 0x00, 0x01, 0x02, 0x00, 0x00, 0x00, 0x00 )
        val victim = MessageCRCService()

        victim.calculateCRC(bytes)

        assertThat(bytes[9]).isEqualTo(0xa9.toByte())
        assertThat(bytes[10]).isEqualTo(0xfa.toByte())
    }

    @Test
    fun `check CRC for valid message returns true`() {
        val bytes = byteArrayOf( 0x00, 0x10, 0x00, 0x1A, 0x00, 0x01, 0x02, 0x00, 0x00, 0xa9.toByte(), 0xfa.toByte() )
        val victim = MessageCRCService()
        assertThat(victim.checkCrc(bytes)).isTrue
    }

    @Test
    fun `check CRC for invalid message returns false`() {
        val bytes = byteArrayOf( 0x00, 0x10, 0x00, 0x1A, 0x00, 0x01, 0x02, 0x00, 0x00, 0xff.toByte(), 0xff.toByte() )
        val victim = MessageCRCService()
        assertThat(victim.checkCrc(bytes)).isFalse
    }
}