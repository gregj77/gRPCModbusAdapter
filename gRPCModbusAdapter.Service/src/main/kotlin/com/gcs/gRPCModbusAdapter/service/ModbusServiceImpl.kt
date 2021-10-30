package com.gcs.gRPCModbusAdapter.service

import io.grpc.stub.StreamObserver
import net.devh.boot.grpc.server.service.GrpcService


@GrpcService
class ModbusServiceImpl : ModbusDeviceServiceGrpc.ModbusDeviceServiceImplBase() {

    override fun subscribeForDeviceData(
        request: DeviceReadRequest,
        responseObserver: StreamObserver<DeviceReadResponse>
    ) {


    }
}