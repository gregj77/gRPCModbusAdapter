package com.gcs.gRPCModbusAdapter.config

import com.gcs.gRPCModbusAdapter.devices.ModbusDevice
import com.gcs.gRPCModbusAdapter.devices.ModbusDeviceImpl
import com.gcs.gRPCModbusAdapter.functions.ModbusFunction
import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriver
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import org.springframework.boot.actuate.health.ReactiveHealthContributor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.context.support.GenericWebApplicationContext
import java.util.function.Supplier
import jakarta.validation.ValidationException

@Configuration
class DeviceManagementConfig(
    appCtx: GenericWebApplicationContext,
    devices: Devices,
    serialPorts: Array<SerialPortDriver>,
    serviceFunctions: Array<ModbusFunction>,
    registry: MeterRegistry) {

    private val configuredDevices: Map<String, ModbusDeviceImpl>

    init {
        var currentEntry: String? = null

        val serviceFunctionsByName = serviceFunctions.associateBy { it.functionName }

        val modbusDevices = try {
            devices
                .entries
                .groupBy { it.name }
                .map {
                    if (it.value.size > 1) throw ValidationException("Device names must be unique - found ${it.value.size} entries for ${it.key}")
                    it.value.first()
                }
                .map { dc ->
                    currentEntry = "device: ${dc.name} -> port: ${dc.serialPort}"
                    val port = serialPorts.first { sp -> sp.name == dc.serialPort }
                    val tags = Tags.of(
                        "name", dc.name,
                        "port", dc.serialPort
                    )
                    val callCounter = registry.counter("modbus_device_function_calls", tags)
                    ModbusDeviceImpl(dc.id, port, dc.name, dc.deviceFunctions, serviceFunctionsByName, callCounter)
                }
                .toList()
        } catch (err: NoSuchElementException) {
            throw ValidationException("Invalid configuration of device-port mapping $currentEntry", err)
        }

        configuredDevices = modbusDevices
            .groupBy { it.deviceId }
            .map {
                if (it.value.size > 1) throw ValidationException("Can't configure ${it.value.size} devices with the same DeviceId: ${it.key}")

                it.value.first()
            }
            .associateBy { it.name }

        configuredDevices
            .values
            .forEach { device ->
                appCtx.registerBean(device.name, ModbusDevice::class.java, Supplier { device })
            }
    }

    @Bean(DeviceHealthContributor)
    fun healthContributors(): Map<String, ReactiveHealthContributor> = configuredDevices

    @Bean(Devices)
    fun devices(): Map<String, ModbusDevice> = configuredDevices

    companion object {
        const val DeviceHealthContributor = "ModbusDeviceHealth"
        const val Devices = "devices"
    }
}