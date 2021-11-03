package com.gcs.gRPCModbusAdapter.service

import com.gcs.gRPCModbusAdapter.devices.DeviceResponse
import com.gcs.gRPCModbusAdapter.devices.ModbusDevice
import io.mockk.InternalPlatformDsl.toArray
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import reactor.core.publisher.Mono
import java.time.Instant

internal class ModbusServiceAdapterTest {

    var victim: ModbusServiceAdapter? = null

    @BeforeEach
    fun setup() {

    }

    @Test
    fun subscribeForDeviceDataThrowsOnInvalidDevice() {
        victim = ModbusServiceAdapter(emptyMap())

        assertThrows<IllegalArgumentException> {
            victim!!.subscribeForDeviceData("non-existing", "dummy", 0)
        }
    }

    @Test
    fun subscribeForDeviceDataThrowsOnInvalidDeviceFunction() {
        val device = mockk<ModbusDevice>()
        every { device.name } returns "dummy"
        every { device.supportsFunction(any()) } returns false
        victim = ModbusServiceAdapter(mapOf(device.name to device))

        assertThrows<IllegalArgumentException> {
            victim!!.subscribeForDeviceData(device.name, "non-existing", 0)
        }
    }

    @Test
    fun subscribeForDeviceDataThrowsOnInvalidInterval() {
        val device = mockk<ModbusDevice>()
        every { device.name } returns "dummy"
        every { device.supportsFunction(eq(com.gcs.gRPCModbusAdapter.devices.DeviceFunction.TOTAL_POWER.name)) } returns true
        victim = ModbusServiceAdapter(mapOf(device.name to device))

        assertThrows<IllegalArgumentException> {
            victim!!.subscribeForDeviceData(device.name, com.gcs.gRPCModbusAdapter.devices.DeviceFunction.TOTAL_POWER.name, -1)
        }
    }

    @Test
    fun subscribeOnZeroIntervalReturnsOneValueOnly() {
        val device = mockk<ModbusDevice>()
        every { device.name } returns "dummy"
        every { device.supportsFunction(eq(com.gcs.gRPCModbusAdapter.devices.DeviceFunction.TOTAL_POWER.name)) } returns true
        every { device.queryDevice(eq(com.gcs.gRPCModbusAdapter.devices.DeviceFunction.TOTAL_POWER)) } returns
                Mono.just(DeviceResponse(device.name, com.gcs.gRPCModbusAdapter.devices.DeviceFunction.TOTAL_POWER, "1", "float", "kWh"))

        victim = ModbusServiceAdapter(mapOf(device.name to device))

        val it = victim!!.subscribeForDeviceData(device.name, DeviceFunction.TOTAL_POWER.name, 0)
            .take(2)
            .toIterable()
            .toList()

        assertThat(it.size).isEqualTo(1)
        assertThat(it[0]).isInstanceOf(DeviceResponse::class.java)
    }

    @Test
    fun subscribeOnOneSecIntervalReturnsStreamOfValues() {
        val device = mockk<ModbusDevice>()
        every { device.name } returns "dummy"
        every { device.supportsFunction(eq(com.gcs.gRPCModbusAdapter.devices.DeviceFunction.TOTAL_POWER.name)) } returns true
        every { device.queryDevice(eq(com.gcs.gRPCModbusAdapter.devices.DeviceFunction.TOTAL_POWER)) } returns
                Mono.just(DeviceResponse(device.name, com.gcs.gRPCModbusAdapter.devices.DeviceFunction.TOTAL_POWER, "1", "float", "kWh"))

        victim = ModbusServiceAdapter(mapOf(device.name to device))

        val now = Instant.now().toEpochMilli()
        val it = victim!!.subscribeForDeviceData(device.name, DeviceFunction.TOTAL_POWER.name, 1)
            .take(3)
            .toIterable()
            .toList()
        val elapsed = Instant.now().toEpochMilli() - now

        assertThat(it.size).isEqualTo(3)
        assertThat(elapsed).isGreaterThanOrEqualTo(3_000)
    }

    @Test
    fun errorsAreMaskedOutFromOutputStream() {
        val device = mockk<ModbusDevice>()
        every { device.name } returns "dummy"
        every { device.supportsFunction(eq(com.gcs.gRPCModbusAdapter.devices.DeviceFunction.TOTAL_POWER.name)) } returns true
        every { device.queryDevice(eq(com.gcs.gRPCModbusAdapter.devices.DeviceFunction.TOTAL_POWER)) } returns
                Mono.error(IllegalStateException("1")) andThenAnswer {
            Mono.error(IllegalStateException("2"))
                } andThenAnswer {
            Mono.just(
                DeviceResponse(
                    device.name,
                    com.gcs.gRPCModbusAdapter.devices.DeviceFunction.TOTAL_POWER,
                    "1",
                    "float",
                    "kWh"
                )
            )
        }

        victim = ModbusServiceAdapter(mapOf(device.name to device))

        val now = Instant.now().toEpochMilli()
        val it = victim!!.subscribeForDeviceData(device.name, DeviceFunction.TOTAL_POWER.name, 1)
            .take(1)
            .toIterable()
            .toList()
        val elapsed = Instant.now().toEpochMilli() - now

        assertThat(it.size).isEqualTo(1)
        assertThat(elapsed).isGreaterThanOrEqualTo(3_000)
    }
}