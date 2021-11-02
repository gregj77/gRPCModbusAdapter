package com.gcs.gRPCModbusAdapter

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan(basePackageClasses = [ModbusAdapter::class])
class ModbusAdapter

fun main(args: Array<String>) {
    runApplication<ModbusAdapter>(*args)
}
