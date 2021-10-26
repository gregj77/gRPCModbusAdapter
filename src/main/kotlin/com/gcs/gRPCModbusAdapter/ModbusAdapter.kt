package com.gcs.gRPCModbusAdapter

import com.gcs.gRPCModbusAdapter.functions.*
import com.gcs.gRPCModbusAdapter.serialPort.SerialCommunicationService
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.stereotype.Component

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan(basePackageClasses = [ModbusAdapter::class])
class ModbusAdapter

fun main(args: Array<String>) {
    runApplication<ModbusAdapter>(*args)
}


@Component
class Dummy(val sp: SerialCommunicationService, val powerFunction: ReadTotalPowerFunction, val readInstantPower: ReadInstantPowerFunction) : CommandLineRunner {
    override fun run(vararg args: String?) {
        println("ok...")

        val port = sp.ports["COM4"]

        var i = 20
        val sleep = 1000L
        while (i-- > 0) {
            println(powerFunction.execute(ReadPowerFunctionArgs(port!!, 1, RegisterId.TOTAL)).get())
            Thread.sleep(sleep)
            println(powerFunction.execute(ReadPowerFunctionArgs(port!!, 1, RegisterId.IMPORT)).get())
            Thread.sleep(sleep)
            println(powerFunction.execute(ReadPowerFunctionArgs(port!!, 1, RegisterId.EXPORT)).get())
            Thread.sleep(sleep)
            println("next call!")
            println(readInstantPower.execute(FunctionArgs(port!!, 1)).get())
        }
    }

}