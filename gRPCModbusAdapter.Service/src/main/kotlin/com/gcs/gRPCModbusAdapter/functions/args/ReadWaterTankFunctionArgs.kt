package com.gcs.gRPCModbusAdapter.functions.args

import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriver

class ReadWaterTankFunctionArgs(driver: SerialPortDriver, deviceId: Byte): ReadFunctionArgs(driver, deviceId, RegisterId.WATER_TANK_AGGREGATED_STATUS, 8) {
    override fun onFormatMessage(request: ByteArray) {
        request[1] = Constants.READREGISTERS
        request[2] = 0x0
        request[3] = 0x0
        request[4] = 0x0
        request[5] = 0x4
    }
}