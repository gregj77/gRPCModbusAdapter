package com.gcs.gRPCModbusAdapter.devices

import com.gcs.gRPCModbusAdapter.functions.ModbusFunction
import com.gcs.gRPCModbusAdapter.functions.ReadCurrentPowerFunction
import com.gcs.gRPCModbusAdapter.functions.ReadCurrentVoltageFunction
import com.gcs.gRPCModbusAdapter.functions.ReadTotalPowerFunction
import com.gcs.gRPCModbusAdapter.functions.args.FunctionArgs
import com.gcs.gRPCModbusAdapter.functions.args.ReadCurrentPowerFunctionArgs
import com.gcs.gRPCModbusAdapter.functions.args.ReadCurrentVoltageFunctionArgs
import com.gcs.gRPCModbusAdapter.functions.args.ReadTotalPowerFunctionArgs
import io.mockk.Matcher
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono

internal class DeviceFunctionsTest {

    var modbusDevice: ModbusDeviceImpl? = null
    var function: ModbusFunction? = null

    @BeforeEach
    fun setup() {
        modbusDevice = mockk<ModbusDeviceImpl>()

        every { modbusDevice!!.deviceId } returns 77
        every { modbusDevice!!.port } returns mockk()
        every { modbusDevice!!.name } returns "mockDevice"
    }

    @Test
    fun queryTotalPowerFunctionIsConfiguredProperly() {
        function = mockk<ReadTotalPowerFunction>()
        every { modbusDevice!!.functionServices } returns mapOf(ReadTotalPowerFunction.FunctionName to function!! )
        every { (function!! as ReadTotalPowerFunction).execute(match(argsMatcher(ReadTotalPowerFunctionArgs::class.java))) } returns Mono.just(123.45f)

        val response = NativeFunctionQuery[DeviceFunction.TOTAL_POWER]!!.invoke(modbusDevice!!).block()!!

        assertThat(response.function).isEqualTo(DeviceFunction.TOTAL_POWER)
        assertThat(response.deviceName).isEqualTo("mockDevice")
        assertThat(response.dataType).isEqualTo("java.lang.Float")
        assertThat(response.unit).isEqualTo("kWh")
        assertThat(response.data).isEqualTo("123.45")
    }

    @Test
    fun queryExportedPowerFunctionIsConfiguredProperly() {
        function = mockk<ReadTotalPowerFunction>()
        every { modbusDevice!!.functionServices } returns mapOf(ReadTotalPowerFunction.FunctionName to function!! )
        every { (function!! as ReadTotalPowerFunction).execute(match(argsMatcher(ReadTotalPowerFunctionArgs::class.java))) } returns Mono.just(123.45f)

        val response = NativeFunctionQuery[DeviceFunction.EXPORT_POWER]!!.invoke(modbusDevice!!).block()!!

        assertThat(response.function).isEqualTo(DeviceFunction.EXPORT_POWER)
        assertThat(response.deviceName).isEqualTo("mockDevice")
        assertThat(response.dataType).isEqualTo("java.lang.Float")
        assertThat(response.unit).isEqualTo("kWh")
        assertThat(response.data).isEqualTo("123.45")
    }

    @Test
    fun queryImportedPowerFunctionIsConfiguredProperly() {
        function = mockk<ReadTotalPowerFunction>()
        every { modbusDevice!!.functionServices } returns mapOf(ReadTotalPowerFunction.FunctionName to function!! )
        every { (function!! as ReadTotalPowerFunction).execute(match(argsMatcher(ReadTotalPowerFunctionArgs::class.java))) } returns Mono.just(123.45f)

        val response = NativeFunctionQuery[DeviceFunction.IMPORT_POWER]!!.invoke(modbusDevice!!).block()!!

        assertThat(response.function).isEqualTo(DeviceFunction.IMPORT_POWER)
        assertThat(response.deviceName).isEqualTo("mockDevice")
        assertThat(response.dataType).isEqualTo("java.lang.Float")
        assertThat(response.unit).isEqualTo("kWh")
        assertThat(response.data).isEqualTo("123.45")
    }

    @Test
    fun queryCurrentPowerFunctionIsConfiguredProperly() {
        function = mockk<ReadCurrentPowerFunction>()
        every { modbusDevice!!.functionServices } returns mapOf(ReadCurrentPowerFunction.FunctionName to function!! )
        every { (function!! as ReadCurrentPowerFunction).execute(match(argsMatcher(ReadCurrentPowerFunctionArgs::class.java))) } returns Mono.just(123.45f)

        val response = NativeFunctionQuery[DeviceFunction.CURRENT_POWER]!!.invoke(modbusDevice!!).block()!!

        assertThat(response.function).isEqualTo(DeviceFunction.CURRENT_POWER)
        assertThat(response.deviceName).isEqualTo("mockDevice")
        assertThat(response.dataType).isEqualTo("java.lang.Float")
        assertThat(response.unit).isEqualTo("W")
        assertThat(response.data).isEqualTo("123.45")
    }

    @Test
    fun queryCurrentVoltagePhase1FunctionIsConfiguredProperly() {
        function = mockk<ReadCurrentVoltageFunction>()
        every { modbusDevice!!.functionServices } returns mapOf(ReadCurrentVoltageFunction.FunctionName to function!! )
        every { (function!! as ReadCurrentVoltageFunction).execute(match(argsMatcher(ReadCurrentVoltageFunctionArgs::class.java))) } returns Mono.just(230.1f)

        val response = NativeFunctionQuery[DeviceFunction.CURRENT_VOLTAGE_PHASE1]!!.invoke(modbusDevice!!).block()!!

        assertThat(response.function).isEqualTo(DeviceFunction.CURRENT_VOLTAGE_PHASE1)
        assertThat(response.deviceName).isEqualTo("mockDevice")
        assertThat(response.dataType).isEqualTo("java.lang.Float")
        assertThat(response.unit).isEqualTo("V")
        assertThat(response.data).isEqualTo("230.1")
    }

    @Test
    fun queryCurrentVoltagePhase2FunctionIsConfiguredProperly() {
        function = mockk<ReadCurrentVoltageFunction>()
        every { modbusDevice!!.functionServices } returns mapOf(ReadCurrentVoltageFunction.FunctionName to function!! )
        every { (function!! as ReadCurrentVoltageFunction).execute(match(argsMatcher(ReadCurrentVoltageFunctionArgs::class.java))) } returns Mono.just(230.2f)

        val response = NativeFunctionQuery[DeviceFunction.CURRENT_VOLTAGE_PHASE2]!!.invoke(modbusDevice!!).block()!!

        assertThat(response.function).isEqualTo(DeviceFunction.CURRENT_VOLTAGE_PHASE2)
        assertThat(response.deviceName).isEqualTo("mockDevice")
        assertThat(response.dataType).isEqualTo("java.lang.Float")
        assertThat(response.unit).isEqualTo("V")
        assertThat(response.data).isEqualTo("230.2")
    }

    @Test
    fun queryCurrentVoltagePhase3FunctionIsConfiguredProperly() {
        function = mockk<ReadCurrentVoltageFunction>()
        every { modbusDevice!!.functionServices } returns mapOf(ReadCurrentVoltageFunction.FunctionName to function!! )
        every { (function!! as ReadCurrentVoltageFunction).execute(match(argsMatcher(ReadCurrentVoltageFunctionArgs::class.java))) } returns Mono.just(230.3f)

        val response = NativeFunctionQuery[DeviceFunction.CURRENT_VOLTAGE_PHASE3]!!.invoke(modbusDevice!!).block()!!

        assertThat(response.function).isEqualTo(DeviceFunction.CURRENT_VOLTAGE_PHASE3)
        assertThat(response.deviceName).isEqualTo("mockDevice")
        assertThat(response.dataType).isEqualTo("java.lang.Float")
        assertThat(response.unit).isEqualTo("V")
        assertThat(response.data).isEqualTo("230.3")
    }

    fun <T: FunctionArgs>argsMatcher(expectedType: Class<T>): Matcher<T> {
        return object: Matcher<T> {
            override fun match(arg: T?): Boolean {
                return arg!!.javaClass == expectedType
            }

        }
    }

}