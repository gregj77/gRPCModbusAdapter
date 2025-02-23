package com.gcs.gRPCModbusAdapter.functions

import com.gcs.gRPCModbusAdapter.functions.args.FunctionArgs
import com.gcs.gRPCModbusAdapter.functions.utils.MessageCRCService
import mu.KLogger
import mu.KotlinLogging
import reactor.core.Exceptions
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.SignalType
import reactor.core.publisher.SynchronousSink
import reactor.util.context.Context
import reactor.util.retry.Retry
import reactor.util.retry.Retry.RetrySignal
import java.time.Duration
import java.time.Instant


interface ModbusFunction {
    val functionName: String
}

abstract class ModbusFunctionBase<in TArgs : FunctionArgs, TResult>(private val crcService: MessageCRCService, private val responseMessageSize: Int, private val logger: KLogger) : ModbusFunction {

    fun execute(args: TArgs): Mono<TResult> {
        logger.debug { "preparing message for ${args.deviceId} to query ${args.registerId} ..." }
        val request = args.toMessage { crcService.calculateCRC(it) }
        logger.debug { "message for ${args.deviceId} to query ${args.registerId} - calculated ${request.size} bytes" }
        val response = ByteArray(responseMessageSize)
        val start = Instant.now().toEpochMilli()
        return args.driver
            .communicateAsync(request)
            .take(response.size.toLong())
            .index()
            .collect({ response }, { buffer, valueAndIndex -> buffer[valueAndIndex.t1.toInt()] = valueAndIndex.t2 })
            .map { extractOrThrow(args, it) }
            .timeout(Duration.ofSeconds(15L))
            .retryWhen(createRetryStrategy())
            .doFinally {
                if (it == SignalType.ON_COMPLETE || it == SignalType.ON_ERROR) {
                    val stop = Instant.now().toEpochMilli()
                    logger.info { "${args.deviceId}.${args.registerId} function $functionName - completed with $it after ${stop - start} ms" }
                }
            }
    }

    private fun extractOrThrow(args: TArgs, response: ByteArray): TResult {
        if (crcService.checkCrc(response)) {
            val result = extractValue(response)
            logger.debug { "got valid response from ${args.deviceId} query ${args.registerId} -> [${response.size} bytes]: $result" }
            return result
        }
        logger.warn { "failed to validate CRC from ${args.deviceId} response query ${args.registerId} - [${response.toHexString()}]" }
        throw CrcCheckError()
    }

    protected abstract fun extractValue(response: ByteArray): TResult

    private fun ByteArray.toHexString(): String {
        return joinToString(separator = " ") { "%02x".format(it) }
    }
    private fun createRetryStrategy(): Retry {
        return Retry.from { companion: Flux<RetrySignal> ->
            companion.handle<Any> { retrySignal: RetrySignal, sink: SynchronousSink<Any> ->
                val ctx: Context = sink.currentContext()
                val left: Int = ctx.getOrDefault("retriesLeft", 2)!!
                if (left > 0 && retrySignal.failure() is CrcCheckError) {
                    logger.debug { "retrying request due to CrcCheck error" }
                    sink.next(Context.of("retriesLeft", left - 1, "lastError", retrySignal.failure()))
                } else {
                    logger.info { "retry quota exceeded - aborting call" }
                    sink.error(Exceptions.retryExhausted("retries exhausted", retrySignal.failure()))
                }
            }
        }
    }

}

