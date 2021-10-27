package com.gcs.gRPCModbusAdapter.functions.utils

internal fun ByteArray.toInt(dataStartIndex: Int): Int {
    require(dataStartIndex >= 0)
    require(this.size - dataStartIndex >= 4)

    var result = this[dataStartIndex].toInt().and(0xff).shl(24)
    result = result.or(this[dataStartIndex+1].toInt().and(0xff).shl(16))
    result = result.or(this[dataStartIndex+2].toInt().and(0xff).shl(8))
    result = result.or(this[dataStartIndex+3].toInt().and(0xff))

    return result
}