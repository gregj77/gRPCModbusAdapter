package com.gcs.gRPCModbusAdapter.functions.args

import com.gcs.gRPCModbusAdapter.functions.watertank.toEnergyBalancePayload
import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriver

class WriteEnergyBalanceRuleArgs(driver: SerialPortDriver, deviceId: Byte, currentBalance: Int) :
    WriteFunctionArgs(driver, deviceId, RegisterId.WATER_TANK_CMD_CONTROL, currentBalance.toEnergyBalancePayload()) {
}