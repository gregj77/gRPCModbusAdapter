package com.gcs.gRPCModbusAdapter.functions.args

import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriver

class ReadCurrentVoltageFunctionArgs(driver: SerialPortDriver, deviceId: Byte, registerId: RegisterId):
    ReadFunctionArgs(driver, deviceId, registerId, 8)