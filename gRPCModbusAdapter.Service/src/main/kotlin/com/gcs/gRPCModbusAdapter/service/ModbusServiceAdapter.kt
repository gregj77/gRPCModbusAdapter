package com.gcs.gRPCModbusAdapter.service

import com.gcs.gRPCModbusAdapter.devices.DeviceFunction
import com.gcs.gRPCModbusAdapter.devices.DeviceResponse
import com.gcs.gRPCModbusAdapter.devices.ModbusDevice
import mu.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.scheduler.Scheduler
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

@Service
class ModbusServiceAdapter(private val devices: Map<String, ModbusDevice>, private val scheduler: Scheduler) {
    private val logger = KotlinLogging.logger {}
    private val delay = AtomicLong(0)

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

        val currentDelay = introduceInitialDelay()
        val query = (if (interval != 0) {
            Flux
                .interval(
                    Duration.ofMillis(currentDelay),
                    Duration.ofSeconds(interval.toLong()),
                    scheduler)
                .flatMap { device.queryDevice(function) }
        } else {
            Flux
                .interval(
                    Duration.ofMillis(currentDelay),
                    scheduler)
                .take(1L)
                .flatMap { device.queryDevice(function) }
        })
            .doOnSubscribe { logger.info { "subscription $deviceName.$functionName activated every $interval s with delay $currentDelay ms" }  }
            .onErrorContinue { err, _ -> logger.warn { "got error $deviceName.$functionName - ${err.message} <${err.javaClass.name}>" } }
            .doOnNext { logger.debug { "next item from $deviceName.$function = $it" } }

        logger.info { "request $deviceName.$functionName validated - will start producing data every $interval seconds" }

        return query
    }

    private fun introduceInitialDelay(): Long = delay.addAndGet(61L)

}