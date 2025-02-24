package com.gcs.gRPCModbusAdapter.functions

import com.gcs.gRPCModbusAdapter.functions.args.CheckStateFunctionArgs
import com.gcs.gRPCModbusAdapter.functions.utils.MessageCRCServiceImpl
import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriver
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers

internal class CheckStateFunctionTest {
    @Test
    fun `function returns expected name`() {
        val victim = CheckStateFunction(mockk<MessageCRCServiceImpl>(), Schedulers.immediate())

        Assertions.assertThat(victim.functionName).isEqualTo("ReadModbusDeviceId")
    }

    @Test
    fun `valid request returns valid response with device value of 0x5 convertible to byte`() {

        val driverMock = mockk<SerialPortDriver>(relaxed = true)
        val crcService = mockk<MessageCRCServiceImpl>(relaxed = true)
        every { crcService.checkCrc(any()) } returns true
        every { driverMock.communicateAsync(any()) } returns Flux.just(
            0x5, 0x3, 0x4, 0x05, 0x0, 0x00, 0xff.toByte(), 0xff.toByte(), 0xff.toByte())
        val victim = CheckStateFunction(crcService, Schedulers.parallel())

        val result = victim.execute(CheckStateFunctionArgs(driverMock, 5)).block()
        Assertions.assertThat(result).isEqualTo(5)
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
            0x5, 0x3, 0x4, 0x00, 0x2, 0x00, 0xff.toByte(), 0xff.toByte(), 0xff.toByte())
        val victim = CheckStateFunction(crcService, Schedulers.parallel())

        assertThrows(CrcCheckError::class.java) {
            try {
                victim.execute(CheckStateFunctionArgs(driverMock, 5)).block()
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