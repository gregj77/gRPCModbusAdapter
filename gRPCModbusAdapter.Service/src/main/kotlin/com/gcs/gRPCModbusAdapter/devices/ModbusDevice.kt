package com.gcs.gRPCModbusAdapter.devices

import com.gcs.gRPCModbusAdapter.functions.ModbusFunction
import com.gcs.gRPCModbusAdapter.functions.ModbusFunctionBase
import com.gcs.gRPCModbusAdapter.functions.args.CheckStateFunctionArgs
import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriver
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.ReactiveHealthIndicator
import reactor.core.publisher.Mono
import java.time.Duration

interface ModbusDevice {
    val name: String

}

class ModbusDeviceImpl(
    val deviceId: Byte,
    private val port: SerialPortDriver,
    override val name: String,
    private val functions: Set<DeviceFunction>,
    private val functionServices: Map<String, ModbusFunction> ) : ModbusDevice, ReactiveHealthIndicator {


    override fun health(): Mono<Health> {
        return Mono
            .create<Health?> {
                if (!port.isRunning) {
                    it.success(Health.down().withDetail("infrastructure", "underlying communication port ${port.name} is not running").build())
                } else {
                    val checkStateFunc = functionServices[DeviceFunction.DEVICE_ID.functionServiceName] as ModbusFunctionBase<CheckStateFunctionArgs, Byte>
                    try {
                        val id = checkStateFunc.execute(CheckStateFunctionArgs(port, deviceId)).get()
                        it.success(Health.up().withDetail("functionResult", "reportedDeviceId: [$id]").build())
                    } catch (error: Exception) {
                        it.success(Health.down().withDetail("functionError", error.message).build())
                    }
//                    checkStateFunc.execute(CheckStateFunctionArgs(port, deviceId)).whenCompleteAsync { id, error ->
//                        if (error != null) { it.success(Health.down().withDetail("functionError", error.message).build()) }
  //                      else { it.success(Health.up().withDetail("functionResult", "reportedDeviceId: [$id]").build()) }
    //                }
                }
            }
            .timeout(Duration.ofSeconds(5L), Mono.just(Health.down().withDetail("functionResult", "timeout after 5 seconds!").build()))
    }
}