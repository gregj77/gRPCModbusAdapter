package com.gcs.gRPCModbusAdapter.functions

import com.gcs.gRPCModbusAdapter.functions.args.ReadCurrentPowerFunctionArgs
import com.gcs.gRPCModbusAdapter.functions.utils.MessageCRCServiceImpl
import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriver
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import reactor.core.publisher.Flux
import java.util.concurrent.ExecutionException

internal class ReadCurrentPowerFunctionTest {

    @Test
    fun `function returns expected name`() {
        val victim = ReadCurrentPowerFunction(mockk<MessageCRCServiceImpl>())

        assertThat(victim.functionName).isEqualTo("ReadCurrentPower")
    }

    @Test
    fun `valid request returns valid response with integer value of 0x000100ff (65791) convertible to float`() {

        val driverMock = mockk<SerialPortDriver>(relaxed = true)
        val crcService = mockk<MessageCRCServiceImpl>(relaxed = true)
        every { crcService.checkCrc(any()) } returns true
        every { driverMock.communicateAsync(any()) } returns Flux.just(
            0x1, 0x3, 0x4, 0x00, 0x1, 0x00, 0xff.toByte(), 0xff.toByte(), 0xff.toByte())
        val victim = ReadCurrentPowerFunction(crcService)

        val result = victim.execute(ReadCurrentPowerFunctionArgs(driverMock, 1)).block()
        assertThat(result).isEqualTo(65791.0f)
        verify { crcService.calculateCRC(any()) }
        verify { crcService.checkCrc(any()) }
        verify { driverMock.communicateAsync(any()) }
        confirmVerified(crcService, driverMock)
    }

    @Test
    fun `response message which failed CRC check will throw CRC check exception`() {

        val driverMock = mockk<SerialPortDriver>(relaxed = true)
        val crcService = mockk<MessageCRCServiceImpl>(relaxed = true)
        every { crcService.checkCrc(any()) } returns false
        every { driverMock.communicateAsync(any()) } returns Flux.just(
            0x1, 0x3, 0x4, 0x00, 0x1, 0x00, 0xff.toByte(), 0xff.toByte(), 0xff.toByte())
        val victim = ReadCurrentPowerFunction(crcService)

        assertThrows(CrcCheckError::class.java) {
            try {
                victim.execute(ReadCurrentPowerFunctionArgs(driverMock, 1)).block()
            } catch (err: Exception) {
                throw err.cause!!
            }
        }
        verify { crcService.calculateCRC(any()) }
        verify { crcService.checkCrc(any()) }
        verify { driverMock.communicateAsync(any()) }
        confirmVerified(crcService, driverMock)
    }
}