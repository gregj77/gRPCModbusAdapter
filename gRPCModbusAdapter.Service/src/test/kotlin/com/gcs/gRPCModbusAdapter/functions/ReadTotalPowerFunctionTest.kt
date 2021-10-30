package com.gcs.gRPCModbusAdapter.functions

import com.gcs.gRPCModbusAdapter.functions.args.ReadTotalPowerFunctionArgs
import com.gcs.gRPCModbusAdapter.functions.args.RegisterId
import com.gcs.gRPCModbusAdapter.functions.utils.MessageCRCService
import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriver
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.rxjava3.core.Observable
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.concurrent.ExecutionException

internal class ReadTotalPowerFunctionTest {
    @Test
    fun `function returns expected name`() {
        val victim = ReadTotalPowerFunction(mockk<MessageCRCService>())

        Assertions.assertThat(victim.functionName).isEqualTo("ReadTotalPower")
    }

    @Test
    fun `valid request returns valid response with integer value of 0x000100ff (65791) convertible to float`() {

        val driverMock = mockk<SerialPortDriver>(relaxed = true)
        val crcService = mockk<MessageCRCService>(relaxed = true)
        every { crcService.checkCrc(any()) } returns true
        every { driverMock.establishStream(any()) } returns Observable.just(
            0x1, 0x3, 0x4, 0x00, 0x1, 0x00, 0xff.toByte(), 0xff.toByte(), 0xff.toByte())
        val victim = ReadTotalPowerFunction(crcService)

        val result = victim.execute(ReadTotalPowerFunctionArgs(driverMock, 1, RegisterId.TOTAL_POWER)).get()
        Assertions.assertThat(result).isEqualTo(657.91f)
        verify { crcService.calculateCRC(any()) }
        verify { crcService.checkCrc(any()) }
        verify { driverMock.establishStream(any()) }
        confirmVerified(crcService, driverMock)
    }

    @Test
    fun `response message which failed CRC check will throw CRC check exception`() {

        val driverMock = mockk<SerialPortDriver>(relaxed = true)
        val crcService = mockk<MessageCRCService>(relaxed = true)
        every { crcService.checkCrc(any()) } returns false
        every { driverMock.establishStream(any()) } returns Observable.just(
            0x1, 0x3, 0x4, 0x00, 0x1, 0x00, 0xff.toByte(), 0xff.toByte(), 0xff.toByte())
        val victim = ReadTotalPowerFunction(crcService)

        assertThrows(CrcCheckError::class.java) {
            try {
                victim.execute(ReadTotalPowerFunctionArgs(driverMock, 1, RegisterId.TOTAL_POWER)).get()
            } catch (err: ExecutionException) {
                throw err.cause!!
            }
        }
        verify { crcService.calculateCRC(any()) }
        verify { crcService.checkCrc(any()) }
        verify { driverMock.establishStream(any()) }
        confirmVerified(crcService, driverMock)
    }
}