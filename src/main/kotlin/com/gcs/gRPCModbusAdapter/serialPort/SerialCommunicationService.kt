package com.gcs.gRPCModbusAdapter.serialPort

import com.gcs.gRPCModbusAdapter.config.Ports
import gnu.io.RXTXPort
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.springframework.stereotype.Service
import java.util.stream.Collectors
import javax.annotation.PreDestroy

@Service
class SerialCommunicationService(configuration: Ports, serialPortFactory: (String) -> RXTXPort) {

    private val ports: Map<String, SerialPortDriver>

    init {
        val scheduler = Schedulers.io()
        ports = configuration
            .entries
            .stream()
            .map { SerialPortDriverImpl(it, scheduler, serialPortFactory) }
            .collect(Collectors.toMap({ p -> p.name}, { p -> p }))

    }

    fun sendDataAndAwaitResponse(dataStream: Observable<ByteArray>) : Observable<Byte> {
        return ports["COM4"]!!.establishStream(dataStream);
    }

    @PreDestroy
    fun cleanupResources() {
        ports.values.forEach(Disposable::dispose)
    }
}