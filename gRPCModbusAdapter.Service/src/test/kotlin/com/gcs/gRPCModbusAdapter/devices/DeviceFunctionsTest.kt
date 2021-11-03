package com.gcs.gRPCModbusAdapter.devices

import com.gcs.gRPCModbusAdapter.functions.ModbusFunction
import com.gcs.gRPCModbusAdapter.functions.ReadCurrentPowerFunction
import com.gcs.gRPCModbusAdapter.functions.ReadTotalPowerFunction
import com.gcs.gRPCModbusAdapter.functions.args.FunctionArgs
import com.gcs.gRPCModbusAdapter.functions.args.ReadCurrentPowerFunctionArgs
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

    fun <T: FunctionArgs>argsMatcher(expectedType: Class<T>): Matcher<T> {
        return object: Matcher<T> {
            override fun match(arg: T?): Boolean {
                return arg!!.javaClass == expectedType
            }

        }
    }

}