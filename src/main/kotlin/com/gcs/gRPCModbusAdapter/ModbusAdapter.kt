package com.gcs.gRPCModbusAdapter

import com.gcs.gRPCModbusAdapter.serialPort.SerialCommunicationService
import io.reactivex.rxjava3.core.Observable
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


//@Component
class Dummy(val sp: SerialCommunicationService) : CommandLineRunner {
    override fun run(vararg args: String?) {
        println("ok...")
        sp
            .sendDataAndAwaitResponse( Observable.just("hello world!!!".toByteArray()))
            .map { Char(it.toInt()) }
            .takeWhile{ it != '!'}
            .subscribe( {
                print(it)
                        }, { }, { println("all done!") })
        sp
            .sendDataAndAwaitResponse(Observable.just("dupa dupa world!!!".toByteArray(Charsets.US_ASCII)))
            .map { Char(it.toInt()) }
            .takeWhile{ it != '!'}
            .subscribe( { print(it) }, { }, { println("all done!") })
    }

}