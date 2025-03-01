package com.gcs.gRPCModbusAdapter.functions

import com.gcs.gRPCModbusAdapter.functions.args.FunctionArgs
import com.gcs.gRPCModbusAdapter.functions.utils.CommunicationLogger
import com.gcs.gRPCModbusAdapter.functions.utils.MessageCRCService
import com.gcs.gRPCModbusAdapter.functions.utils.toHexString
import mu.KLogger
import org.springframework.beans.factory.annotation.Autowired
import reactor.core.Exceptions
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.SignalType
import reactor.core.scheduler.Scheduler
import reactor.util.retry.Retry
import reactor.util.retry.Retry.RetrySignal
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger


interface ModbusFunction {
    val functionName: String
}

val executionId = AtomicInteger(0)

abstract class ModbusFunctionBase<in TArgs : FunctionArgs, TResult>(
    private val crcService: MessageCRCService,
    private val responseMessageSize: Int,
    private val logger: KLogger,
    private val scheduler: Scheduler) : ModbusFunction{

    @Autowired
    lateinit var payloadLogger: CommunicationLogger

    fun execute(args: TArgs): Mono<TResult> {
        val id = executionId.incrementAndGet()
        logger.debug { "[$id] preparing message for ${args.deviceId} to query ${args.registerId} ..." }
        val request = args.toMessage { crcService.calculateCRC(it) }
        logger.debug { "[$id] message for ${args.deviceId} to query ${args.registerId} - calculated ${request.size} bytes" }
        val response = ByteArray(responseMessageSize)
        val start = Instant.now().toEpochMilli()

        return Mono.defer {
            args.driver
                .communicateAsync(request)
                .take(response.size.toLong())
                .index()
                .collect({ response }, { buffer, valueAndIndex -> buffer[valueAndIndex.t1.toInt()] = valueAndIndex.t2 })
                .doOnNext {payloadLogger.logCommunication(request, it) }
        }
            .flatMap { extractOrThrow(id, args, it) }
            .retryWhen(createRetryStrategy(id))
            .timeout(Duration.ofSeconds(5), scheduler)
            .doFinally {signalType ->
                val shouldLog  = when (signalType) {
                    SignalType.ON_COMPLETE -> true
                    SignalType.ON_ERROR -> true
                    SignalType.CANCEL -> true
                    else -> false
                }
                if (shouldLog) {
                    val stop = Instant.now().toEpochMilli()
                    logger.info { "[$id] ${args.deviceId}.${args.registerId} function $functionName - completed with $signalType after ${stop - start} ms" }
                }
            }
    }

    private fun extractOrThrow(id: Int, args: TArgs, response: ByteArray): Mono<TResult> {
        if (crcService.checkCrc(response)) {

            if (!args.responseDeviceMatchesRequest(response)) {
                logger.warn { "[$id] deviceId ${args.deviceId} in response does not match requested deviceId ${response[0]} [${response.toHexString()}]" }
                return Mono.error(DeviceIdCheckError(args.deviceId, response[0]))
            }

            val result = extractValue(response)
            logger.debug { "[$id] got valid response from ${args.deviceId} query ${args.registerId} -> [${response.size} bytes]: $result, [${response.toHexString()}]" }
            return Mono.just(result)
        }

        logger.debug { "[$id] failed to validate CRC from ${args.deviceId} response query ${args.registerId} - [${response.toHexString()}]" }
        return Mono.error(CrcCheckError())
    }

    protected abstract fun extractValue(response: ByteArray): TResult

    private fun createRetryStrategy(id : Int): Retry {
        return Retry.from { companion: Flux<RetrySignal> ->
            companion.flatMap { retrySignal ->
                val retries = retrySignal.totalRetries()
                val failure = retrySignal.failure()

                if (retries < 2 && failure is CheckError) {
                    logger.debug { "[$id] retrying request due to '${failure.message}' error" }
                    Mono.delay(Duration.ofMillis(100L), scheduler).thenReturn(retrySignal)
                } else{
                    logger.warn { "[$id] retry quota exceeded - aborting call" }
                    Mono.error(Exceptions.retryExhausted("retries exhausted", failure))
                }
            }
        }
    }
}

