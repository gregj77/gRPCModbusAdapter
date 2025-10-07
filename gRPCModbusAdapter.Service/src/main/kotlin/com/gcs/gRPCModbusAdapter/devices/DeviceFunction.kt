package com.gcs.gRPCModbusAdapter.devices

import com.gcs.gRPCModbusAdapter.functions.*
import com.gcs.gRPCModbusAdapter.functions.args.*
import reactor.core.publisher.Mono

enum class DeviceFunction(val functionName: String, val registerId: RegisterId, val unit: String, val functionServiceName: String) {
    TOTAL_POWER("totalPower", RegisterId.TOTAL_POWER, "kWh", ReadTotalPowerFunction.FunctionName),
    EXPORT_POWER("producedPower", RegisterId.EXPORT_POWER, "kWh", ReadTotalPowerFunction.FunctionName),
    IMPORT_POWER("consumedPower", RegisterId.IMPORT_POWER, "kWh", ReadTotalPowerFunction.FunctionName),
    CURRENT_POWER("currentPower", RegisterId.EXPORT_POWER, "W", ReadCurrentPowerFunction.FunctionName),
    DEVICE_ID("deviceId", RegisterId.DEVICE_ID, "N/A", CheckStateFunction.FunctionName),
    CURRENT_VOLTAGE_PHASE1("currentVoltagePhase1", RegisterId.CURRENT_VOLTAGE_PHASE1, "V", ReadCurrentVoltageFunction.FunctionName),
    CURRENT_VOLTAGE_PHASE2("currentVoltagePhase2", RegisterId.CURRENT_VOLTAGE_PHASE2, "V", ReadCurrentVoltageFunction.FunctionName),
    CURRENT_VOLTAGE_PHASE3("currentVoltagePhase3", RegisterId.CURRENT_VOLTAGE_PHASE3, "V", ReadCurrentVoltageFunction.FunctionName),
    CURRENT_AMPERAGE_PHASE1("currentAmperagePhase1", RegisterId.CURRENT_AMPERAGE_PHASE1, "A", ReadCurrentAmperageFunction.FunctionName),
    CURRENT_AMPERAGE_PHASE2("currentAmperagePhase2", RegisterId.CURRENT_AMPERAGE_PHASE2, "A", ReadCurrentAmperageFunction.FunctionName),
    CURRENT_AMPERAGE_PHASE3("currentAmperagePhase3", RegisterId.CURRENT_AMPERAGE_PHASE3, "A", ReadCurrentAmperageFunction.FunctionName),
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

private fun queryCurrentVoltage(sender: ModbusDeviceImpl, registerId: RegisterId, deviceFunction: DeviceFunction): Mono<DeviceResponse> {
    val function = sender.functionServices[ReadCurrentVoltageFunction.FunctionName] as ReadCurrentVoltageFunction
    return function
        .execute(ReadCurrentVoltageFunctionArgs(sender.port, sender.deviceId, registerId))
        .map { DeviceResponse(sender.name, deviceFunction, it.toString(), it.javaClass.name, deviceFunction.unit) }
}

private fun queryCurrentAmperage(sender: ModbusDeviceImpl, registerId: RegisterId, deviceFunction: DeviceFunction): Mono<DeviceResponse> {
    val function = sender.functionServices[ReadCurrentAmperageFunction.FunctionName] as ReadCurrentAmperageFunction
    return function
        .execute(ReadCurrentAmperageFunctionArgs(sender.port, sender.deviceId, registerId))
        .map { DeviceResponse(sender.name, deviceFunction, it.toString(), it.javaClass.name, deviceFunction.unit) }
}

internal val NativeFunctionQuery = mapOf<DeviceFunction, (ModbusDeviceImpl) -> Mono<DeviceResponse>>(
    DeviceFunction.TOTAL_POWER to ::queryTotalPower,
    DeviceFunction.EXPORT_POWER to ::queryExportedPower,
    DeviceFunction.IMPORT_POWER to ::queryImportedPower,
    DeviceFunction.CURRENT_POWER to ::queryCurrentPower,
    DeviceFunction.CURRENT_VOLTAGE_PHASE1 to { queryCurrentVoltage(it, RegisterId.CURRENT_VOLTAGE_PHASE1, DeviceFunction.CURRENT_VOLTAGE_PHASE1) },
    DeviceFunction.CURRENT_VOLTAGE_PHASE2 to { queryCurrentVoltage(it, RegisterId.CURRENT_VOLTAGE_PHASE2, DeviceFunction.CURRENT_VOLTAGE_PHASE2) },
    DeviceFunction.CURRENT_VOLTAGE_PHASE3 to { queryCurrentVoltage(it, RegisterId.CURRENT_VOLTAGE_PHASE3, DeviceFunction.CURRENT_VOLTAGE_PHASE3) },
    DeviceFunction.CURRENT_AMPERAGE_PHASE1 to { queryCurrentAmperage(it, RegisterId.CURRENT_AMPERAGE_PHASE1, DeviceFunction.CURRENT_AMPERAGE_PHASE1) },
    DeviceFunction.CURRENT_AMPERAGE_PHASE2 to { queryCurrentAmperage(it, RegisterId.CURRENT_AMPERAGE_PHASE2, DeviceFunction.CURRENT_AMPERAGE_PHASE2) },
    DeviceFunction.CURRENT_AMPERAGE_PHASE3 to { queryCurrentAmperage(it, RegisterId.CURRENT_AMPERAGE_PHASE3, DeviceFunction.CURRENT_AMPERAGE_PHASE3) },
)