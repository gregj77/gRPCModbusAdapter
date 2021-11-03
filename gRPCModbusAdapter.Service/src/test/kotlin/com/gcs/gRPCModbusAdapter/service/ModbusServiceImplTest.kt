package com.gcs.gRPCModbusAdapter.service

import com.gcs.gRPCModbusAdapter.devices.DeviceFunction
import com.gcs.gRPCModbusAdapter.devices.DeviceResponse
import com.gcs.gRPCModbusAdapter.devices.ModbusDevice
import io.grpc.stub.ServerCallStreamObserver
import io.grpc.stub.StreamObserver
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux

internal class ModbusServiceImplTest {


    @Test
    fun invalidInputParametersResultInError() {
        val device = mockk<ModbusDevice>()
        every { device.name } returns "dummy"
        every { device.supportsFunction(eq(DeviceFunction.TOTAL_POWER.name)) } returns true
        every { device.supportsFunction(eq(DeviceFunction.CURRENT_POWER.name)) } returns false
        val adapter = ModbusServiceAdapter(mapOf(device.name to device))

        val victim = ModbusServiceImpl(adapter)

        var error: Throwable? = null
        val observer = object : StreamObserver<Response> {
            override fun onNext(p0: Response?) {
                throw IllegalStateException("not expected in this context!")
            }

            override fun onError(p0: Throwable?) {
                error = p0
            }

            override fun onCompleted() {
                throw IllegalStateException("not expected in this context!")
            }
        }

        victim.subscribeForDeviceData(request(), observer)

        assertThat(error).isNotNull.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun validConfigurationCreatesValidStreamAndMergesAllResponses() {
        val adapter = mockk<ModbusServiceAdapter>()

        every { adapter.subscribeForDeviceData(eq("dummy"), eq("TOTAL_POWER"), eq(0)) } returns
                Flux.just(response(DeviceFunction.TOTAL_POWER))

        every { adapter.subscribeForDeviceData(eq("dummy"), eq("CURRENT_POWER"), eq(1)) } returns
                Flux.just(response(DeviceFunction.CURRENT_POWER), response(DeviceFunction.CURRENT_POWER), response(DeviceFunction.CURRENT_POWER))


        val victim = ModbusServiceImpl(adapter)

        val items = mutableListOf<Response>()
        var completeCalled = false
        var onCancelHandler: Runnable? = null

        val observer = object : ServerCallStreamObserver<Response>() {
            override fun onNext(p0: Response?) {
                items.add(p0!!)
            }

            override fun onError(p0: Throwable?) {
                throw IllegalStateException("not expected in this context!")
            }

            override fun onCompleted() {
                completeCalled = true
            }

            override fun isReady(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setOnReadyHandler(p0: Runnable?) {
                TODO("Not yet implemented")
            }

            override fun disableAutoInboundFlowControl() {
                TODO("Not yet implemented")
            }

            override fun request(p0: Int) {
                TODO("Not yet implemented")
            }

            override fun setMessageCompression(p0: Boolean) {
                TODO("Not yet implemented")
            }

            override fun isCancelled(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setOnCancelHandler(p0: Runnable?) {
                onCancelHandler = p0
            }

            override fun setCompression(p0: String?) {
                TODO("Not yet implemented")
            }
        }

        victim.subscribeForDeviceData(request(), observer)

        assertThat(completeCalled).isTrue
        assertThat(items.size).isEqualTo(4)
        assertThat(items.count { it.functionName.name == "TOTAL_POWER" }).isEqualTo(1)
        assertThat(items.count { it.functionName.name == "CURRENT_POWER" }).isEqualTo(3)

        assertThat(onCancelHandler).isNotNull
        onCancelHandler?.run()
    }

    fun request(): Query {
        return Query
            .newBuilder()
            .addRequest(DeviceRequest
                .newBuilder()
                .setDeviceName("dummy")
                .addReadRequests(Request
                    .newBuilder()
                    .setFunctionName(com.gcs.gRPCModbusAdapter.service.DeviceFunction.TOTAL_POWER)
                    .setReadIntervalInSeconds(0)
                    .build())
                .addReadRequests(Request
                    .newBuilder()
                    .setFunctionName(com.gcs.gRPCModbusAdapter.service.DeviceFunction.CURRENT_POWER)
                    .setReadIntervalInSeconds(1)
                    .build())
                .build())
            .build()
    }

    fun response(function: DeviceFunction): DeviceResponse {
        return DeviceResponse("dummy", function, "0", "-", "Float" )
    }
}