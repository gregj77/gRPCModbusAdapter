package com.gcs.gRPCModbusAdapter.service

import com.gcs.gRPCModbusAdapter.devices.DeviceFunction
import com.gcs.gRPCModbusAdapter.devices.DeviceResponse
import com.gcs.gRPCModbusAdapter.devices.ModbusDevice
import mu.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.time.Duration

@Service
class ModbusServiceAdapter(private val devices: Map<String, ModbusDevice>) {
    private val logger = KotlinLogging.logger {}

    fun subscribeForDeviceData(deviceName: String, functionName: String, interval: Int): Flux<DeviceResponse> {

        logger.info { "validating request $deviceName.$functionName ${ if (interval==0) "ONCE" else "every $interval seconds"}..." }
        val device = if (devices.containsKey(deviceName)) {
            devices[deviceName]!!
        } else {
            logger.warn { "trying to query non-existing device $deviceName" }
            throw IllegalArgumentException("device = $deviceName")
        }

        val function = if (device.supportsFunction(functionName)) {
            DeviceFunction.valueOf(functionName)
        } else {
            logger.warn { "device $deviceName doesn't support function $functionName!" }
            throw IllegalArgumentException("functionName = $functionName")
        }

        if (interval < 0) {
            logger.warn { "query interval $deviceName.$functionName must be >= 0" }
            throw IllegalArgumentException("interval = $interval")
        }

        val query = (if (interval != 0) {
            Flux.interval(Duration.ofSeconds(interval.toLong())).flatMap { device.queryDevice(function) }
        } else {
            Flux.defer { device.queryDevice(function) }
        })
            .onErrorContinue { err, _ -> logger.warn { "got error $deviceName.$functionName - ${err.message} <${err.javaClass.name}>" } }
            .doOnNext { logger.debug { "next item from $functionName.$function = $it" } }

        logger.info { "request $deviceName.$functionName validated - will start producing data every $interval" }

        return query
    }

}