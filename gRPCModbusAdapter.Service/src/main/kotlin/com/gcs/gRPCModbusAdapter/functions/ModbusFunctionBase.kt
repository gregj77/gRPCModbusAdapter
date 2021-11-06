package com.gcs.gRPCModbusAdapter.functions

import com.gcs.gRPCModbusAdapter.functions.args.FunctionArgs
import com.gcs.gRPCModbusAdapter.functions.utils.MessageCRCService
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.time.Duration

interface ModbusFunction {
    val functionName: String
}

abstract class ModbusFunctionBase<in TArgs : FunctionArgs, TResult>(private val crcService: MessageCRCService, private val responseMessageSize: Int) : ModbusFunction{
    private val logger = KotlinLogging.logger(this.javaClass.name)

    fun execute(args: TArgs): Mono<TResult> {
        logger.debug { "preparing message for ${args.deviceId} to query ${args.registerId} ..." }
        val request = args.toMessage { crcService.calculateCRC(it) }
        logger.debug { "message for ${args.deviceId} to query ${args.registerId} - calculated ${request.size} bytes" }
        val response = ByteArray(responseMessageSize)
        var idx = 0
        return args.driver
            .communicateAsync(request)
            .take(response.size.toLong())
            .collect( { response }, { buffer, byte -> buffer[idx++] = byte })
            .map { extractOrThrow(args, it) }
            .timeout(Duration.ofSeconds(15L))
    }

    private fun extractOrThrow(args: TArgs, response: ByteArray): TResult {
        if (crcService.checkCrc(response)) {
            val result = extractValue(response)
            logger.debug { "got valid response from ${args.deviceId} query ${args.registerId} -> [${response.size} bytes]: $result" }
            return result
        }
        logger.warn { "failed to validate CRC from ${args.deviceId} response query ${args.registerId}" }
        throw CrcCheckError()
    }

    protected abstract fun extractValue(response: ByteArray): TResult
}

