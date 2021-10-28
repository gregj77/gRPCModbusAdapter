package com.gcs.gRPCModbusAdapter.functions

class CrcCheckError : Exception("CRC check failed on incoming message")