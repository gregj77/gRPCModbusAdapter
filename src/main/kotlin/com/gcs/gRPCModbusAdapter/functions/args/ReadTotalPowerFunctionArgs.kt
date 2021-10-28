package com.gcs.gRPCModbusAdapter.functions.args

import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriver

class ReadTotalPowerFunctionArgs(driver: SerialPortDriver, deviceId: Byte, registerId: RegisterId): ReadFunctionArgs(driver, deviceId, Constants.checkRegisterBelongsToTotalPower(registerId), 8)