package com.gcs.gRPCModbusAdapter.devices

import com.gcs.gRPCModbusAdapter.functions.*
import com.gcs.gRPCModbusAdapter.functions.args.*
import com.gcs.gRPCModbusAdapter.functions.watertank.RuleType
import com.gcs.gRPCModbusAdapter.functions.watertank.toMask
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
    WATER_TANK_COMBINED_STATUS("waterTankStatus", RegisterId.WATER_TANK_AGGREGATED_STATUS, "JSON", ReadWaterTankStatus.FunctionName),
}

enum class DeviceCommand(val commandName: String, val registerId: RegisterId, val functionServiceName: String) {
    UPDATE_WATER_TANK_RULES("updateWaterTankRules", RegisterId.WATER_TANK_CMD_CONTROL, UpdateWaterTankRulesFunction.FunctionName),
    NOTIFY_WATER_TANK_WITH_ENERGY_BALANCE("notifyWaterTankWithEnergyBalance", RegisterId.WATER_TANK_CMD_CONTROL, NotifyWaterTankWithEnergyBalanceFunction.FunctionName),
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

private fun queryWaterTankStatus(sender: ModbusDeviceImpl): Mono<DeviceResponse> {
    val function = sender.functionServices[ReadWaterTankStatus.FunctionName] as ReadWaterTankStatus
    return function
        .execute(ReadWaterTankFunctionArgs(sender.port, sender.deviceId))
        .map { DeviceResponse(sender.name, DeviceFunction.WATER_TANK_COMBINED_STATUS, it.toString(), it.javaClass.name, DeviceFunction.WATER_TANK_COMBINED_STATUS.unit) }
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
    DeviceFunction.WATER_TANK_COMBINED_STATUS to ::queryWaterTankStatus,
)


internal val NativeCommands = mapOf<DeviceCommand, (ModbusDeviceImpl, Any) -> Mono<Boolean>>(
    DeviceCommand.UPDATE_WATER_TANK_RULES to { device, data -> executeCmdSetWaterTankRules(device, data as List<String>) } ,
    DeviceCommand.NOTIFY_WATER_TANK_WITH_ENERGY_BALANCE to { device, data -> executeCmdNotifyEnergyBalance(device, data as Int) },
)

private fun executeCmdNotifyEnergyBalance(sender: ModbusDeviceImpl, energyBalance: Int): Mono<Boolean> {
    val function = sender.functionServices[DeviceCommand.NOTIFY_WATER_TANK_WITH_ENERGY_BALANCE.functionServiceName] as NotifyWaterTankWithEnergyBalanceFunction
    return function.execute(WriteEnergyBalanceRuleArgs(sender.port, sender.deviceId, energyBalance))
}

private fun executeCmdSetWaterTankRules(sender: ModbusDeviceImpl, ruleNames: List<String>): Mono<Boolean> {
    val function = sender.functionServices[DeviceCommand.UPDATE_WATER_TANK_RULES.functionServiceName] as UpdateWaterTankRulesFunction
    val ruleNames = ruleNames.map { RuleType.valueOf(it) }
    return function.execute(WriteWaterTankRulesArgs(sender.port, sender.deviceId, ruleNames))
}