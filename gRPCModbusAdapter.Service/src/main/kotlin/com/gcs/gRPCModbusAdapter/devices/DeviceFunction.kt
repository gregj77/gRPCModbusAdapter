package com.gcs.gRPCModbusAdapter.devices

import com.gcs.gRPCModbusAdapter.functions.CheckStateFunction
import com.gcs.gRPCModbusAdapter.functions.ReadCurrentPowerFunction
import com.gcs.gRPCModbusAdapter.functions.ReadTotalPowerFunction
import com.gcs.gRPCModbusAdapter.functions.args.RegisterId

enum class DeviceFunction(val functionName: String, val registerId: RegisterId, val unit: String, val functionServiceName: String) {
    TOTAL_POWER("totalPower", RegisterId.TOTAL_POWER, "kWh", ReadTotalPowerFunction.FunctionName),
    PRODUCED_POWER("producedPower", RegisterId.EXPORT_POWER, "kWh", ReadTotalPowerFunction.FunctionName),
    CONSUMED_POWER("consumedPower", RegisterId.IMPORT_POWER, "kWh", ReadTotalPowerFunction.FunctionName),
    CURRENT_POWER("currentPower", RegisterId.EXPORT_POWER, "W", ReadCurrentPowerFunction.FunctionName),
    DEVICE_ID("deviceId", RegisterId.DEVICE_ID, "N/A", CheckStateFunction.FunctionName),
}