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
    fun serialPortFactory(): (String) -> RXTXPort {
        return { portName ->
            val portId = (CommPortIdentifier
                .getPortIdentifiers()
                .asSequence() as Sequence<CommPortIdentifier>)
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
}