package com.gcs.gRPCModbusAdapter.service

import com.google.protobuf.Timestamp
import io.grpc.stub.ServerCallStreamObserver
import io.grpc.stub.StreamObserver
import mu.KotlinLogging
import net.devh.boot.grpc.server.service.GrpcService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
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

    override fun writeDeviceCommand(request: Command?, responseObserver: StreamObserver<CommandResponse>?) {
        writeDeviceCommandInternal(request!!).subscribe { (result, fqn, errorMessage) ->
            publishResponse(
                result,
                fqn,
                errorMessage,
                responseObserver!!
            )
        }
    }

    private fun writeDeviceCommandInternal(request: Command) : Mono<Triple<Boolean, String, String?>> {
        val deviceName = request.deviceName
        val commandName = request.command.name
        val fqn ="${deviceName}.${commandName}"
        return try {
            val payload: Any = if (request.hasIntPayload()) {
                request.intPayload
            } else if (request.hasItems()) {
                request.items.itemList.map { it }
            } else {
                throw IllegalArgumentException("command payload must be either intPayload or items")
            }

            logger.debug { "received command request for $fqn with payload ${payload.javaClass.name} - validating and executing command..." }

            deviceAdapter.writeDeviceCommand(deviceName, commandName, payload)
                .map { Triple<Boolean, String, String?>(it, fqn, null) }
                .onErrorResume { err -> Mono.just(Triple<Boolean, String, String?>(false, fqn, "failed to execute command $fqn- ${err.message} <${err.javaClass.name}>")) }
        } catch (err: Exception) {
            Mono.just(Triple(false,  fqn,"failed to execute command $fqn - ${err.message} <${err.javaClass.name}>"))
        }
    }

    private fun buildResponse(result: Boolean, message: String?): CommandResponse {
        val now = Instant.now()
        return CommandResponse
            .newBuilder()
            .setTime(Timestamp.newBuilder().setNanos(now.nano).setSeconds(now.epochSecond).build())
            .setSuccess(result)
            .apply { message?.let { setMessage(message) } }
            .build()
    }

     private fun publishResponse(result: Boolean, fqn: String, message: String?, responseObserver: StreamObserver<CommandResponse>) {
         val response = buildResponse(result, message)
         logger.debug { "processing $fqn completed with $result" }
         responseObserver.onNext(response)
         responseObserver.onCompleted()
     }
}