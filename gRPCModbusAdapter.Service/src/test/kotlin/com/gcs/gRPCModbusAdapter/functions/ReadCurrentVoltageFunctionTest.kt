package com.gcs.gRPCModbusAdapter.functions

import com.gcs.gRPCModbusAdapter.functions.args.ReadCurrentVoltageFunctionArgs
import com.gcs.gRPCModbusAdapter.functions.args.ReadTotalPowerFunctionArgs
import com.gcs.gRPCModbusAdapter.functions.args.RegisterId
import com.gcs.gRPCModbusAdapter.functions.utils.MessageCRCServiceImpl
import com.gcs.gRPCModbusAdapter.functions.utils.NOPCommunicationLogger
import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriver
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.util.concurrent.ExecutionException

internal class ReadCurrentVoltageFunctionTest {
    @Test
    fun `function returns expected name`() {
        val victim = ReadCurrentVoltageFunction(mockk<MessageCRCServiceImpl>(), Schedulers.immediate())

        Assertions.assertThat(victim.functionName).isEqualTo("ReadCurrentVoltage")
    }

    @ParameterizedTest
    @EnumSource(value = RegisterId::class, names = ["CURRENT_VOLTAGE_PHASE1", "CURRENT_VOLTAGE_PHASE2", "CURRENT_VOLTAGE_PHASE3"])
    fun `valid request returns valid response with short value of 0x091b (2331) convertible to float`(registerId: RegisterId) {

        val driverMock = mockk<SerialPortDriver>(relaxed = true)
        val crcService = mockk<MessageCRCServiceImpl>(relaxed = true)
        every { crcService.checkCrc(any()) } returns true
        every { driverMock.communicateAsync(any()) } returns Flux.just(
            0x1, 0x3, 0x4, 0x09, 0x1b, 0x00, 0x00, 0xff.toByte(), 0xff.toByte())
        val victim = ReadCurrentVoltageFunction(crcService, Schedulers.parallel())
        victim.payloadLogger = NOPCommunicationLogger()

        val result = victim.execute(ReadCurrentVoltageFunctionArgs(driverMock, 1, registerId)).block()
        Assertions.assertThat(result).isEqualTo(233.1f)
        verify { crcService.calculateCRC(any()) }
        verify { crcService.checkCrc(any()) }
        verify { driverMock.communicateAsync(any()) }
        confirmVerified(crcService, driverMock)
    }

    @ParameterizedTest
    @EnumSource(value = RegisterId::class, names = ["CURRENT_VOLTAGE_PHASE1", "CURRENT_VOLTAGE_PHASE2", "CURRENT_VOLTAGE_PHASE3"])
    fun `response message which failed CRC check will throw CRC check exception`(registerId: RegisterId) {

        val driverMock = mockk<SerialPortDriver>(relaxed = true)
        val crcService = mockk<MessageCRCServiceImpl>(relaxed = true)
        every { crcService.checkCrc(any()) } returns false
        every { driverMock.communicateAsync(any()) } returns Flux.just(
            0x1, 0x3, 0x4, 0x09, 0x1b, 0x00, 0x00, 0xff.toByte(), 0xff.toByte())
        val victim = ReadCurrentVoltageFunction(crcService, Schedulers.parallel())
        victim.payloadLogger = NOPCommunicationLogger()

        assertThrows(CrcCheckError::class.java) {
            try {
                victim.execute(ReadCurrentVoltageFunctionArgs(driverMock, 1, registerId)).block()
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