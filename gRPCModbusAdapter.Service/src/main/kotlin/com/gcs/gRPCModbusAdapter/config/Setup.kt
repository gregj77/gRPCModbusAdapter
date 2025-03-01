package com.gcs.gRPCModbusAdapter.config

import com.fazecast.jSerialComm.SerialPort
import com.gcs.gRPCModbusAdapter.ModbusAdapter
import gnu.io.CommPortIdentifier
import gnu.io.RXTXPort
import gnu.io.ensurePortIsClosed
import mu.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.annotation.PreDestroy

@Configuration
class Setup {
    private val logger = KotlinLogging.logger {}
    private val portInternalCloseStore: ConcurrentHashMap<String, () -> Unit> = ConcurrentHashMap()

    @Bean
    fun serialPortFactory(commPorts: () -> Array<CommPortIdentifier>): (String) -> RXTXPort {
        return { portName ->
            val portId = lookupPort(commPorts, portName)

            if (portId == null) {
                logger.error { "can't find serial port $portName installed on the system" }
                throw IllegalArgumentException("Port $portName not found!")
            }
            portInternalCloseStore[portName] = { ensurePortIsClosed(portId) }
            portId.open(ModbusAdapter::class.simpleName, 0)
        }
    }

    @Bean
    fun v2SerialPortFactory(commPorts: () -> Array<CommPortIdentifier>): (String) -> SerialPort {
        return { portName ->
            val portId = lookupPort(commPorts, portName)
            if (portId == null) {
                logger.error { "can't find serial port $portName installed on the system" }
                throw IllegalArgumentException("Port $portName not found!")
            }
            SerialPort.getCommPort(portId.name)
        }
    }

    private fun lookupPort(commPorts: () -> Array<CommPortIdentifier>, portName: String): CommPortIdentifier? {
        return commPorts
            .invoke()
            .filter { it.portType == CommPortIdentifier.PORT_SERIAL && it.name == portName }
            .take(1)
            .firstOrNull()
    }

    @Bean
    fun commPortEnumerator() :() -> Array<CommPortIdentifier> {
        return {
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
            result
        }
    }

    @Bean
    fun hardwareErrorPortCleaner(): (String) -> Unit {
        return { portName ->
            if (portInternalCloseStore.containsKey(portName)) {
                portInternalCloseStore.remove(portName)?.invoke()
            }
        }
    }

    @Bean
    fun scheduler(): Scheduler {
        return Schedulers.boundedElastic()
    }

    @PreDestroy
    fun cleanup() {
    }
}