package com.gcs.gRPCModbusAdapter

import gnu.io.CommPortIdentifier
import gnu.io.SerialPort
import gnu.io.SerialPortEvent
import gnu.io.SerialPortEventListener
import mu.KotlinLogging
import java.io.InputStream
import java.io.OutputStream
import javax.annotation.PreDestroy

//@Service
class MeterDataService : SerialPortEventListener {
    private val logger = KotlinLogging.logger {}
    private val portId: CommPortIdentifier
    private val serialPort: SerialPort
    private val input: InputStream
    private val output: OutputStream


    init {
        portId = (CommPortIdentifier
            .getPortIdentifiers()
            .asSequence() as Sequence<CommPortIdentifier>)
            .filter { it.portType == CommPortIdentifier.PORT_SERIAL && it.name == "COM4"}
            .take(1)
            .first()

        serialPort = portId.open(MeterDataService::javaClass.name, 1_000)
        serialPort.setSerialPortParams(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE )
        serialPort.addEventListener(this)
        serialPort.notifyOnDataAvailable(true)
        input = serialPort.inputStream
        output = serialPort.outputStream


//        for (portId in (
//            logger.info { "found port: ${portId.name}" }


//            current = portId
  //      }
        //this.portId = current!!
        //portId = CommPortIdentifier.getPortIdentifier("COM3")
    }

    override fun serialEvent(args: SerialPortEvent) {
        logger.info { "got: ${args.eventType}" }
        when (args.eventType) {
            SerialPortEvent.DATA_AVAILABLE -> {
                val readBuffer = ByteArray(64)
                while (input.available() > 0) {
                    input.read(readBuffer)
                }
                logger.info { "received message: ${String(readBuffer)}" }

            }
        }
    }

    @PreDestroy
    fun onDestroy() {
        serialPort.close()
    }
}