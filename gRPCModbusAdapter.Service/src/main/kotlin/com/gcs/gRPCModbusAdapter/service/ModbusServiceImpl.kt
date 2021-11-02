package com.gcs.gRPCModbusAdapter.service

import com.google.protobuf.Timestamp
import io.grpc.stub.ServerCallStreamObserver
import io.grpc.stub.StreamObserver
import mu.KotlinLogging
import net.devh.boot.grpc.server.service.GrpcService
import reactor.core.publisher.Flux
import java.time.Instant


@GrpcService
class ModbusServiceImpl(private val deviceAdapter: ModbusServiceAdapter) : ModbusDeviceServiceGrpc.ModbusDeviceServiceImplBase() {
    private val logger = KotlinLogging.logger {}

    override fun subscribeForDeviceData(
        request: Query,
        responseObserver: StreamObserver<Response>
    ) {

        try {
            val requests = request
                .requestList
                .flatMap { dr ->
                    dr.readRequestsList.map { r ->
                        deviceAdapter.subscribeForDeviceData(dr.deviceName, r.functionName.name, r.readIntervalInSeconds)
                    }
                }
                .map { queryStream ->
                    queryStream.map { dr ->
                        val now = Instant.now()
                        Response
                            .newBuilder()
                            .setTime(Timestamp.newBuilder().setNanos(now.nano).setSeconds(now.epochSecond).build())
                            .setDataType(dr.dataType)
                            .setUnit(dr.unit)
                            .setFunctionName(com.gcs.gRPCModbusAdapter.service.DeviceFunction.valueOf(dr.function.name))
                            .setDeviceName(dr.deviceName)
                            .setValue(dr.data)
                            .build()
                    }
                }
                .toList()

            logger.info { "successfully created ${requests.size} data streams to serve data" }

            val subscriptionToken = Flux
                .merge(requests)
                .subscribe(
                    { notificationData -> responseObserver.onNext(notificationData) },
                    { err -> responseObserver.onError(err) },
                    { responseObserver.onCompleted() }
                )

            responseObserver as ServerCallStreamObserver<Response>
            responseObserver.setOnCancelHandler {
                logger.info { "client cancelled subscription - disposing subscription" }
                subscriptionToken.dispose()
            }

        } catch (err: Exception) {
            logger.error { "failed to create request stream - ${err.message} <${err.javaClass.name}>" }
            responseObserver.onError(err)
        }
    }
}