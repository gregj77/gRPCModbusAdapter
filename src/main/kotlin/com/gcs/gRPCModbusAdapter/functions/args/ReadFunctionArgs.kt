package com.gcs.gRPCModbusAdapter.functions.args

import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriver

abstract class ReadFunctionArgs(driver: SerialPortDriver, deviceId: Byte, registerId: RegisterId, messageSize: Int): FunctionArgs(driver, deviceId, registerId, messageSize) {
    override fun onFormatMessage(request: ByteArray) {
        request[1] = Constants.READFUNCTION
        request[4] = 0x0 //data number first byte
        request[5] = 0x2 //data number second byte
    }
}