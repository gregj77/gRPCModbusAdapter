package com.gcs.gRPCModbusAdapter.functions.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class ByteArrayUtilsKtTest {

    @Test
    fun `(int) can convert valid byte array to int`() {

        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val result = bytes.toInt(0)
        assertThat(result).isEqualTo(0x01020304)
    }

    @Test
    fun `(int) convert valid byte array with negative start index will throw`() {

        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        assertThrows<IllegalArgumentException> { bytes.toInt(-1) }
    }

    @Test
    fun `(int) convert of too small byte array with valid start index will throw`() {

        val bytes = byteArrayOf(0x01, 0x02, 0x03)
        assertThrows<IllegalArgumentException> { bytes.toInt(1) }
    }

    @Test
    fun `(int) convert of valid byte array with too far start index will throw`() {

        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        assertThrows<IllegalArgumentException> { bytes.toInt(2) }
    }

    @Test
    fun `(short) can convert valid byte array to short`() {

        val bytes = byteArrayOf(0x01, 0x02)
        val result = bytes.toShort(0)
        assertThat(result).isEqualTo(0x0102)
    }

    @Test
    fun `(short) convert valid byte array with negative start index will throw`() {

        val bytes = byteArrayOf(0x01, 0x02)
        assertThrows<IllegalArgumentException> { bytes.toShort(-1) }
    }

    @Test
    fun `(short) convert of too small byte array with valid start index will throw`() {

        val bytes = byteArrayOf(0x01, 0x02)
        assertThrows<IllegalArgumentException> { bytes.toShort(1) }
    }

    @Test
    fun `(short) convert of valid byte array with too far start index will throw`() {

        val bytes = byteArrayOf(0x01, 0x02)
        assertThrows<IllegalArgumentException> { bytes.toInt(2) }
    }
}