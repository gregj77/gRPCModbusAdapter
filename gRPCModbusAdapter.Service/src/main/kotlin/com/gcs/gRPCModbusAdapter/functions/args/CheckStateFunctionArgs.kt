package com.gcs.gRPCModbusAdapter.functions.args

import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriver

class CheckStateFunctionArgs(driver: SerialPortDriver, deviceId: Byte) : ReadFunctionArgs(driver, deviceId, RegisterId.DEVICE_ID, 8)