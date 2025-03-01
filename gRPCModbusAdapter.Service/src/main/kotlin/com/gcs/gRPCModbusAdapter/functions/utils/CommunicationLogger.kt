package com.gcs.gRPCModbusAdapter.functions.utils

import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

interface CommunicationLogger {
    fun logCommunication(request: ByteArray, response: ByteArray)
}

@Component
@Profile("!ExtendedLogging")
class NOPCommunicationLogger : CommunicationLogger {
    override fun logCommunication(request: ByteArray, response: ByteArray) {
    }

}

@Component
@Profile("ExtendedLogging")
class CommunicationLoggerImpl : CommunicationLogger {

    private val logger = KotlinLogging.logger{}
    private val mapper = ObjectMapper()

    init {
        logger.trace { mapper.writeValueAsString(Payload("hello", "world")) }
    }
    override fun logCommunication(request: ByteArray, response: ByteArray) {
        logger.trace { mapper.writeValueAsString(Payload(request.toHexString(), response.toHexString())) }
    }
}

private data class Payload(val request: String, val response: String)