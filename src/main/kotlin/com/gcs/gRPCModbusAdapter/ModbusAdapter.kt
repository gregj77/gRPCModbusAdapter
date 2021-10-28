package com.gcs.gRPCModbusAdapter

import com.gcs.gRPCModbusAdapter.functions.*
import com.gcs.gRPCModbusAdapter.functions.args.ReadCurrentPowerFunctionArgs
import com.gcs.gRPCModbusAdapter.functions.args.ReadTotalPowerFunctionArgs
import com.gcs.gRPCModbusAdapter.functions.args.RegisterId
import com.gcs.gRPCModbusAdapter.serialPort.SerialPortDriver
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Profile
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
@Profile("debug")
class Dummy(val appCtx: ApplicationContext, val powerFunction: ReadTotalPowerFunction, val readInstantPower: ReadCurrentPowerFunction) : CommandLineRunner {
    override fun run(vararg args: String?) {
        println("ok...")

        val  port = appCtx.getBean("COM4") as SerialPortDriver

        var i = 200
        val sleep = 1000L
        while (i-- > 0) {
            println(powerFunction.execute(ReadTotalPowerFunctionArgs(port!!, 1, RegisterId.TOTAL_POWER)).get())
            Thread.sleep(sleep)
            println(powerFunction.execute(ReadTotalPowerFunctionArgs(port!!, 1, RegisterId.IMPORT_POWER)).get())
            Thread.sleep(sleep)
            println(powerFunction.execute(ReadTotalPowerFunctionArgs(port!!, 1, RegisterId.EXPORT_POWER)).get())
            Thread.sleep(sleep)
            println("next call!")
            println(readInstantPower.execute(ReadCurrentPowerFunctionArgs(port!!, 1)).get())
        }
    }

}