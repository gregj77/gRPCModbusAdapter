package com.gcs.gRPCModbusAdapter.functions.args

import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriver

class ReadCurrentPowerFunctionArgs(driver: SerialPortDriver, deviceId: Byte): ReadFunctionArgs(driver, deviceId, RegisterId.CURRENT_POWER, 8)