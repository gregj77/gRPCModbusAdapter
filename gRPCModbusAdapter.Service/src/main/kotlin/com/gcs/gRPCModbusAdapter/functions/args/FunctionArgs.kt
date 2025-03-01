package com.gcs.gRPCModbusAdapter.functions.args

import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriver

abstract class FunctionArgs(val driver: SerialPortDriver, val deviceId: Byte, val registerId: RegisterId, private val requestMessageSize: Int) {

    fun toMessage( onBufferReady: ((buffer: ByteArray) -> Unit)? = null ): ByteArray {
        require( requestMessageSize >= 8)

        val request = ByteArray(requestMessageSize)
        request[0] = deviceId
        request[1] = 0xff.toByte() // to be filled by subclass - operation type - either read or write
        request[2] = 0x0
        request[3] = registerId.value //register second byte
        request[4] = 0xff.toByte() // request data size high-byte - to be set by subclass
        request[5] = 0xff.toByte() // request data size low-byte - to be set by subclass

        onFormatMessage(request)
        onBufferReady?.invoke(request)

        require(request[1] != 0xff.toByte()) { "Operation type not configured! "}
        require(request[4] != 0xff.toByte()) { "Request data size high-byte not configured! "}
        require(request[5] != 0xff.toByte()) { "Request data size low-byte not configured! "}

        return request
    }

    protected abstract fun onFormatMessage(request: ByteArray)

    fun responseDeviceMatchesRequest(response: ByteArray): Boolean {
        return response.size > 1 && deviceId == response[0]
    }
}