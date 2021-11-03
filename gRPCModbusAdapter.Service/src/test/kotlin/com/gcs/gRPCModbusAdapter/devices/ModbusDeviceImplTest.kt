package com.gcs.gRPCModbusAdapter.devices

import com.gcs.gRPCModbusAdapter.functions.ModbusFunction
import com.gcs.gRPCModbusAdapter.functions.ModbusFunctionBase
import com.gcs.gRPCModbusAdapter.functions.ReadTotalPowerFunction
import com.gcs.gRPCModbusAdapter.functions.args.CheckStateFunctionArgs
import com.gcs.gRPCModbusAdapter.functions.args.ReadTotalPowerFunctionArgs
import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriver
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.actuate.health.Status
import reactor.core.publisher.Mono
import java.lang.IllegalArgumentException
import java.lang.RuntimeException

internal class ModbusDeviceImplTest {

    val deviceId: Byte = 9
    val deviceName = "fooDevice"
    var serialPort: SerialPortDriver? = null
    var victim: ModbusDeviceImpl? = null
    var function: ModbusFunction? = null
    var functions: Map<String, ModbusFunction>? = null
    var meterRegistry: MeterRegistry? = null
    var checkStateFunction: ModbusFunctionBase<CheckStateFunctionArgs, Byte>? = null

    @BeforeEach
    fun setup() {
        serialPort = mockk<SerialPortDriver>()
        every { serialPort!!.name } returns "com1"
        meterRegistry = SimpleMeterRegistry()

        checkStateFunction = mockk<ModbusFunctionBase<CheckStateFunctionArgs, Byte>>()

        function = mockk<ReadTotalPowerFunction>()


        functions = mapOf(
            DeviceFunction.DEVICE_ID.functionServiceName to checkStateFunction!!,
            DeviceFunction.TOTAL_POWER.functionServiceName to function!!
        )

        victim = ModbusDeviceImpl(deviceId, serialPort!!, deviceName, setOf(DeviceFunction.DEVICE_ID, DeviceFunction.TOTAL_POWER), functions!!, meterRegistry!!.counter("test"))
    }

    @Test
    fun `reported health is DOWN when serial port is down`() {
        every { serialPort!!.isRunning } returns false

        assertThat(victim!!.health().block()!!.status).isEqualTo(Status.DOWN)
    }

    @Test
    fun `reported health is DOWN when checkStateFunction reports error`() {
        every { serialPort!!.isRunning } returns true
        every { checkStateFunction!!.execute(any()) } returns Mono.just(99)

        assertThat(victim!!.health().block()!!.status).isEqualTo(Status.DOWN)
    }

    @Test
    fun `reported health is UP when checkStateFunction returns success`() {
        every { serialPort!!.isRunning } returns true
        every { checkStateFunction!!.execute(any()) } returns Mono.just(deviceId)

        assertThat(victim!!.health().block()!!.status).isEqualTo(Status.UP)
    }

    @Test
    fun `not supported function by device will throw`() {
        assertThrows<IllegalArgumentException> {
            victim!!.queryDevice(DeviceFunction.CURRENT_POWER).block()
        }
        assertThat(victim!!.commandsProcessed).isEqualTo(1)
    }

    @Test
    fun `attempt to call non-native function will throw`() {
        assertThrows<IllegalStateException> {
            victim!!.queryDevice(DeviceFunction.DEVICE_ID).block()
        }
        assertThat(victim!!.commandsProcessed).isEqualTo(1)
    }

    @Test
    fun `call to supported function returns function's data`() {
        every { (function as ReadTotalPowerFunction).execute(any()) } returns Mono.just(123.45f)
        val result = victim!!.queryDevice(DeviceFunction.TOTAL_POWER).block()
        assertThat(victim!!.commandsProcessed).isEqualTo(1)

        assertThat(result!!.deviceName).isEqualTo(deviceName)
        assertThat(result!!.function).isEqualTo(DeviceFunction.TOTAL_POWER)
        assertThat(result!!.data).isEqualTo(123.45f.toString())
        assertThat(result!!.dataType).isEqualTo("java.lang.Float")
        assertThat(result!!.unit).isEqualTo(DeviceFunction.TOTAL_POWER.unit)
    }

    @Test
    fun `supportsFunction returns true for supported function`() {
        assertThat(victim!!.supportsFunction("TOTAL_POWER")).isTrue
    }

    @Test
    fun `supportsFunction returns false for not supported function`() {
        assertThat(victim!!.supportsFunction("IMPORT_POWER")).isTrue
    }

}