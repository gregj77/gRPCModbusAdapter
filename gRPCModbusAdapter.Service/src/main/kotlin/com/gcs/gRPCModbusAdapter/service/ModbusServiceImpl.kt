package com.gcs.gRPCModbusAdapter.service

import com.gcs.gRPCModbusAdapter.devices.DeviceFunction
import com.gcs.gRPCModbusAdapter.devices.DeviceResponse
import com.gcs.gRPCModbusAdapter.devices.ModbusDevice
import com.google.protobuf.Timestamp
import io.grpc.stub.ServerCallStreamObserver
import io.grpc.stub.StreamObserver
import mu.KotlinLogging
import net.devh.boot.grpc.server.service.GrpcService
import reactor.core.publisher.Flux
import java.time.Duration
import java.time.Instant


@GrpcService
class ModbusServiceImpl(private val devices: Map<String, ModbusDevice>) : ModbusDeviceServiceGrpc.ModbusDeviceServiceImplBase() {
    private val logger = KotlinLogging.logger {}

    override fun subscribeForDeviceData(
        request: DeviceReadRequest,
        responseObserver: StreamObserver<DeviceReadResponse>
    ) {

        if (!devices.containsKey(request.deviceName)) {
            logger.warn { "Invalid device requested: ${request.deviceName}" }
            responseObserver.onError(IllegalArgumentException("Device ${request.deviceName} not found!"))
            return
        }

        val device = devices[request.deviceName]!!

        val subscriptions = mutableListOf<Flux<DeviceResponse>>()
        request.readRequestsList.forEach {

            val internalFunction = DeviceFunction.valueOf(it.functionName.name)
            if (it.readIntervalInSeconds == 0) {
                subscriptions.add(device.queryDevice(internalFunction).flux())
            } else {
                subscriptions.add(Flux
                    .interval(Duration.ofSeconds(it.readIntervalInSeconds.toLong()))
                    .flatMap { device.queryDevice(internalFunction) })
            }
        }

        val subscription = Flux
            .merge(subscriptions)
            .map {
                val now = Instant.now()
                DeviceReadResponse
                    .newBuilder()
                    .setTime(Timestamp.newBuilder().setNanos(now.nano).setSeconds(now.epochSecond).build())
                    .setValue(it.data)
                    .setDataType(it.dataType)
                    .setUnit(it.unit)
                    .setFunctionName(com.gcs.gRPCModbusAdapter.service.DeviceFunction.valueOf(it.function.name))
                    .build()
            }
            .subscribe { responseObserver.onNext(it) }

        responseObserver as ServerCallStreamObserver<DeviceReadResponse>
        responseObserver.setOnCancelHandler {
            logger.info { "client side cancellation requested" }
            subscription.dispose()
        }
    }
}