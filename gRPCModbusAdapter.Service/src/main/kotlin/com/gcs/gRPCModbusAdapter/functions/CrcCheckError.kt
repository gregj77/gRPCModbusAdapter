package com.gcs.gRPCModbusAdapter.functions


abstract class CheckError(msg: String): Exception(msg)
class CrcCheckError : CheckError("CRC check failed on incoming message")
class DeviceIdCheckError(expected: Byte, actual: Byte): CheckError("Expected $expected DeviceId does not match actual $actual")