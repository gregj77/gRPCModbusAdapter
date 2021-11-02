package com.gcs.gRPCModbusAdapter.devices

import com.gcs.gRPCModbusAdapter.functions.CheckStateFunction
import com.gcs.gRPCModbusAdapter.functions.ReadCurrentPowerFunction
import com.gcs.gRPCModbusAdapter.functions.ReadTotalPowerFunction
import com.gcs.gRPCModbusAdapter.functions.args.ReadCurrentPowerFunctionArgs
import com.gcs.gRPCModbusAdapter.functions.args.ReadTotalPowerFunctionArgs
import com.gcs.gRPCModbusAdapter.functions.args.RegisterId
import reactor.core.publisher.Mono

enum class DeviceFunction(val functionName: String, val registerId: RegisterId, val unit: String, val functionServiceName: String) {
    TOTAL_POWER("totalPower", RegisterId.TOTAL_POWER, "kWh", ReadTotalPowerFunction.FunctionName),
    EXPORT_POWER("producedPower", RegisterId.EXPORT_POWER, "kWh", ReadTotalPowerFunction.FunctionName),
    IMPORT_POWER("consumedPower", RegisterId.IMPORT_POWER, "kWh", ReadTotalPowerFunction.FunctionName),
    CURRENT_POWER("currentPower", RegisterId.EXPORT_POWER, "W", ReadCurrentPowerFunction.FunctionName),
    DEVICE_ID("deviceId", RegisterId.DEVICE_ID, "N/A", CheckStateFunction.FunctionName),
}

private fun queryTotalPower(sender: ModbusDeviceImpl) : Mono<DeviceResponse> {
    val function = sender.functionServices[ReadTotalPowerFunction.FunctionName] as ReadTotalPowerFunction
    return function
        .execute(ReadTotalPowerFunctionArgs(sender.port, sender.deviceId, RegisterId.TOTAL_POWER))
        .map { DeviceResponse(sender.name, DeviceFunction.TOTAL_POWER, it.toString(), it.javaClass.name, DeviceFunction.TOTAL_POWER.unit) }
}

private fun queryExportedPower(sender: ModbusDeviceImpl) : Mono<DeviceResponse> {
    val function = sender.functionServices[ReadTotalPowerFunction.FunctionName] as ReadTotalPowerFunction
    return function
        .execute(ReadTotalPowerFunctionArgs(sender.port, sender.deviceId, RegisterId.EXPORT_POWER))
        .map { DeviceResponse(sender.name, DeviceFunction.EXPORT_POWER, it.toString(), it.javaClass.name, DeviceFunction.EXPORT_POWER.unit) }
}

private fun queryImportedPower(sender: ModbusDeviceImpl) : Mono<DeviceResponse> {
    val function = sender.functionServices[ReadTotalPowerFunction.FunctionName] as ReadTotalPowerFunction
    return function
        .execute(ReadTotalPowerFunctionArgs(sender.port, sender.deviceId, RegisterId.IMPORT_POWER))
        .map { DeviceResponse(sender.name, DeviceFunction.IMPORT_POWER, it.toString(), it.javaClass.name, DeviceFunction.IMPORT_POWER.unit) }
}

private fun queryCurrentPower(sender: ModbusDeviceImpl) : Mono<DeviceResponse> {
    val function = sender.functionServices[ReadCurrentPowerFunction.FunctionName] as ReadCurrentPowerFunction
    return function
        .execute(ReadCurrentPowerFunctionArgs(sender.port, sender.deviceId))
        .map { DeviceResponse(sender.name, DeviceFunction.CURRENT_POWER, it.toString(), it.javaClass.name, DeviceFunction.CURRENT_POWER.unit) }
}

internal val NativeFunctionQuery = mapOf<DeviceFunction, (ModbusDeviceImpl) -> Mono<DeviceResponse>>(
    DeviceFunction.TOTAL_POWER to ::queryTotalPower,
    DeviceFunction.EXPORT_POWER to ::queryExportedPower,
    DeviceFunction.IMPORT_POWER to ::queryImportedPower,
    DeviceFunction.CURRENT_POWER to ::queryCurrentPower
)