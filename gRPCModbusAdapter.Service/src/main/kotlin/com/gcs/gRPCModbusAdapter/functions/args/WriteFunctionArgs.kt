package com.gcs.gRPCModbusAdapter.functions.args

import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriver

abstract class WriteFunctionArgs(driver: SerialPortDriver, deviceId: Byte, registerId: RegisterId, private val payload: ByteArray):
    FunctionArgs(driver, deviceId, registerId, 7 + payload.size + 2) {
    override fun onFormatMessage(request: ByteArray) {
        request[1] = Constants.WRITEREGISTERS

        val registerCount = payload.size / 2
        request[4] = (registerCount shr 8).toByte()
        request[5] = (registerCount and 0xff).toByte()
        request[6] = payload.size.toByte()
        payload.copyInto(request, 7)
    }
}