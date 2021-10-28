package com.gcs.gRPCModbusAdapter.config

import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriver
import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriverImpl
import gnu.io.RXTXPort
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.springframework.boot.actuate.health.HealthContributor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.context.support.GenericWebApplicationContext
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors
import javax.annotation.PreDestroy

@Configuration
class SerialPortManagementConfig(configuration: Ports, serialPortFactory: (String) -> RXTXPort, appCtx: GenericWebApplicationContext, private val registry: MeterRegistry) {

    private val ports: Map<String, SerialPortDriverImpl>

    init {
        val scheduler = Schedulers.io()
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
                val driver = SerialPortDriverImpl(it, scheduler, serialPortFactory, writeCounter, readCounter)
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

    companion object {
        const val SerialPortsHealthContributor = "SerialPortHealth"
    }
}