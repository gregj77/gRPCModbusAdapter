package com.gcs.gRPCModbusAdapter.config

import com.fazecast.jSerialComm.SerialPort
import com.gcs.gRPCModbusAdapter.serialPort.CommandHandlerFactory
import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriver
import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriverImpl
import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriverImplV2
import gnu.io.RXTXPort
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import org.springframework.boot.actuate.health.HealthContributor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.web.context.support.GenericWebApplicationContext
import reactor.core.Disposable
import reactor.core.scheduler.Scheduler
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors
import javax.annotation.PreDestroy

@Configuration
class SerialPortManagementConfig(
    appCtx: GenericWebApplicationContext,
    configuration: Ports,
    portCreator: SerialPortCreator,
    registry: MeterRegistry) {

    private val ports: Map<String, SerialPortDriver>

    init {
        ports = configuration
            .entries
            .stream()
            .map {
                val tags = Tags.of(
                    "name", it.name,
                    "baudRate", it.baudRate.toString(),
                    "parity", it.parity.toString(),
                    "dataBits", it.dataBits.toString(),
                    "stopBits", it.stopBits.toString())

                val writeCounter = registry.counter("serial_port_bytes_written", tags)
                val readCounter = registry.counter("serial_port_bytes_read", tags)
                val driver = portCreator.create(it, writeCounter, readCounter)
                registry.gauge("serial_port_is_up", tags, driver) { p -> if (p.isRunning) 1.0 else 0.0 }
                appCtx.registerBean(it.name, SerialPortDriver::class.java, Supplier { driver })
                return@map driver
            }
            .collect(Collectors.toMap(SerialPortDriver::name, Function.identity()))
    }

    @PreDestroy
    private fun cleanupResources() {
        ports.values.forEach(Disposable::dispose)
    }

    @Bean(SerialPortsHealthContributor)
    fun healthContributors(): Map<String, HealthContributor> = ports

    @Bean
    fun serialPorts(): Array<SerialPortDriver> = ports.values.toTypedArray()

    companion object {
        const val SerialPortsHealthContributor = "SerialPortHealth"
    }
}

@Configuration
class SerialPortDriverFactory(
    private val hardwareErrorPortCleaner: (String) -> Unit,
    private val scheduler: Scheduler,
    private val v1SerialPortFactory: (String) -> RXTXPort,
    private val v2SerialPortFactory: (String) -> SerialPort,

    ) {

    @Bean
    @Primary
    @Profile("!v2Driver")
    fun v2DriverFactory() : SerialPortCreator {
        return object :SerialPortCreator {
            override fun create(
                config: SerialPortConfig,
                writtenBytesCounter: Counter,
                readBytesCounter: Counter
            ): SerialPortDriver {
                val commandHandlerFactory = CommandHandlerFactory(config, writtenBytesCounter, readBytesCounter)
                return SerialPortDriverImpl(config, scheduler, v1SerialPortFactory, hardwareErrorPortCleaner, commandHandlerFactory)
            }
        }
    }

    @Bean
    @Profile("v2Driver")
    fun v1DriverFactory() : SerialPortCreator {
        return object :SerialPortCreator {
            override fun create(
                config: SerialPortConfig,
                writtenBytesCounter: Counter,
                readBytesCounter: Counter
            ): SerialPortDriver {
                return SerialPortDriverImplV2(config, scheduler, v2SerialPortFactory, writtenBytesCounter, readBytesCounter)
            }
        }
    }
}

interface SerialPortCreator {
    fun create(config: SerialPortConfig, writtenBytesCounter: Counter, readBytesCounter: Counter): SerialPortDriver
}
