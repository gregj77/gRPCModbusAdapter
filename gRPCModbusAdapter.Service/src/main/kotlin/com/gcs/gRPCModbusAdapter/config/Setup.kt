package com.gcs.gRPCModbusAdapter.config

import com.gcs.gRPCModbusAdapter.ModbusAdapter
import gnu.io.CommPortIdentifier
import gnu.io.RXTXPort
import mu.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class Setup {
    private val logger = KotlinLogging.logger {}

    @Bean
    fun serialPortFactory(commPorts: Array<CommPortIdentifier>): (String) -> RXTXPort {
        return { portName ->
            val portId = commPorts
                .filter { it.portType == CommPortIdentifier.PORT_SERIAL && it.name == portName}
                .take(1)
                .firstOrNull()

            if (portId == null) {
                logger.error { "can't find serial port $portName installed on the system" }
                throw IllegalArgumentException("Port $portName not found!")
            }
            portId.open(ModbusAdapter::class.simpleName, 0)
        }
    }

    @Bean
    fun commPortEnumerator() :Array<CommPortIdentifier> {
        logger.info { "looking up available serial ports...." }
        val result = CommPortIdentifier.getPortIdentifiers()
            .asSequence()
            .map {
                val result = it as CommPortIdentifier
                logger.info { "found serial port ${result.name}" }
                return@map result
            }
            .toList()
            .toTypedArray()
        logger.info { "found ${result.size} port(s)" }
        return result
    }
}