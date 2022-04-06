package com.gcs.gRPCModbusAdapter.devices

import com.gcs.gRPCModbusAdapter.functions.ModbusFunction
import com.gcs.gRPCModbusAdapter.functions.ModbusFunctionBase
import com.gcs.gRPCModbusAdapter.functions.args.CheckStateFunctionArgs
import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriver
import io.micrometer.core.instrument.Counter
import mu.KotlinLogging
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.ReactiveHealthIndicator
import reactor.core.publisher.Mono
import java.time.Duration

interface ModbusDevice {
    val name: String
    val commandsProcessed: Int

    fun queryDevice(function: DeviceFunction): Mono<DeviceResponse>
    fun supportsFunction(functionName: String): Boolean
}

data class DeviceResponse(val deviceName: String, val function: DeviceFunction, val data: String, val dataType: String, val unit: String)

class ModbusDeviceImpl(
    val deviceId: Byte,
    internal val port: SerialPortDriver,
    override val name: String,
    private val functions: Set<DeviceFunction>,
    internal val functionServices: Map<String, ModbusFunction>,
    private val callCounter: Counter,
) : ModbusDevice, ReactiveHealthIndicator {
    private val logger = KotlinLogging.logger {}

    override fun health(): Mono<Health> {
        if (!port.isRunning) {
            return Mono.just(Health.down().withDetail("infrastructure", "underlying communication port ${port.name} is not running").build())
        }
        val checkStateFunc =  @Suppress("UNCHECKED_CAST")(functionServices[DeviceFunction.DEVICE_ID.functionServiceName] as ModbusFunctionBase<CheckStateFunctionArgs, Byte>)
        return checkStateFunc
            .execute(CheckStateFunctionArgs(port, deviceId))
            .map { if (it != deviceId) throw IllegalStateException("Invalid device id") else it }
            .map { Health.up().build() }
            .timeout(Duration.ofSeconds(5L))
            .onErrorResume { Mono.just(Health.down().withDetail("error", it.message).build()) }
    }

    override val commandsProcessed: Int
        get() = callCounter.count().toInt()

    override fun queryDevice(function: DeviceFunction): Mono<DeviceResponse> {
        if (!functions.contains(function)) {
            logger.warn { "Device $name doesn't support function $function" }
            callCounter.increment()
            return Mono.error(IllegalArgumentException("Function $function is not supported by $name"))
        }

        if (!NativeFunctionQuery.containsKey(function)) {
            logger.warn { "FunctionToQuery mapping doesn't contain entry for $function" }
            callCounter.increment()
            return Mono.error(IllegalStateException("Configuration issue - FunctionToQuery doesn't contain $function mapping!"))
        }

        return NativeFunctionQuery[function]!!
            .invoke(this)
            .doOnEach { if (it.isOnNext or it.isOnError) callCounter.increment() }
    }

    override fun supportsFunction(functionName: String): Boolean {
        return try {
            DeviceFunction.valueOf(functionName)
            true
        } catch (error: Exception) {
            false
        }
    }
}