package com.gcs.gRPCModbusAdapter.functions

import io.reactivex.rxjava3.core.Observable
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture

@Service
class ReadInstantPowerFunction(private val crcService: MessageCRCService) : ModbusFunction<FunctionArgs, Float> {
    private val logger = KotlinLogging.logger {  }
    private val READFUNCTION: Byte = 3

    override val functionName: String
        get() = "ReadTotalPower"

    override fun execute(args: FunctionArgs): CompletableFuture<Float> {
        val request = ByteArray(8)
        request[0] = args.deviceId
        request[1] = READFUNCTION
        request[2] = 0x0 //register first byte
        request[3] = 0x86.toByte()// register second byte
        request[4] = 0x0 //data number first byte
        request[5] = 0x2 //data number second byte
        crcService.calculateCRC(request)

        val response = ByteArray(9)
        var idx = 0
        return args.driver
            .establishStream(Observable.just(request))
            .take(response.size.toLong())
            .collect( { response }, { buffer, byte -> buffer[idx++] = byte })
            .map(this::extractOrThrow)
            .toCompletionStage()
            .toCompletableFuture()
    }

    private fun extractOrThrow(response: ByteArray): Float {
        if (crcService.checkCrc(response)) {
            return extractValue(response)
        }
        logger.warn { "failed to validate CRC for response" }
        throw CrcCheckError()
    }

    private fun extractValue(response: ByteArray): Float {
        var result = response[3].toInt().and(0xff).shl(24)
        result = result.or(response[4].toInt().and(0xff).shl(16))
        result = result.or(response[5].toInt().and(0xff).shl(8))
        result = result.or(response[6].toInt().and(0xff))
        logger.debug { "extracted value ${result.toInt()}" }
        return result.toFloat() / 1000.0f
    }
}