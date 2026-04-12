package com.gcs.gRPCModbusAdapter.functions.args

import com.gcs.gRPCModbusAdapter.functions.watertank.RuleType
import com.gcs.gRPCModbusAdapter.functions.watertank.toWaterTankPayload
import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriver

class WriteWaterTankRulesArgs(driver: SerialPortDriver, deviceId: Byte, rules: Iterable<RuleType>):
    WriteFunctionArgs(driver, deviceId, RegisterId.WATER_TANK_CMD_CONTROL, rules.toWaterTankPayload())
