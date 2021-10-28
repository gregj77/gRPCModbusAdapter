package com.gcs.gRPCModbusAdapter.serialPort

import com.gcs.gRPCModbusAdapter.config.Parity
import com.gcs.gRPCModbusAdapter.config.SerialPortConfig
import com.gcs.gRPCModbusAdapter.config.StopBits
import gnu.io.PortInUseException
import gnu.io.RXTXPort
import gnu.io.SerialPortEvent
import gnu.io.SerialPortEventListener
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.schedulers.TestScheduler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

internal class SerialPortDriverImplTest {

    var serialPortFactory: ((String) -> RXTXPort)? = null
    var serialPortMock: RXTXPort? = null
    var scheduler: TestScheduler? = null
    var writeCounter: Counter? = null
    var readCounter: Counter? = null

    @BeforeEach
    fun setUp() {
        scheduler = TestScheduler()
        serialPortFactory = { serialPortMock!! }
        val registry = SimpleMeterRegistry()
        writeCounter = registry.counter("bytesWritten")
        readCounter = registry.counter("bytesRead")
    }

    @AfterEach
    fun tearDown() {
        serialPortFactory = null
    }

    @Test
    fun `trying to create a non existing port results in an error when trying to submit data`() {
        val cfg = SerialPortConfig("FOO", 9600, 8, Parity.NONE, StopBits.STOPBITS_1)

        serialPortFactory = { throw IllegalArgumentException("no such port $it")}

        val victim = SerialPortDriverImpl(cfg, Schedulers.io(), serialPortFactory!!, writeCounter!!, readCounter!!)

        assertThat(victim.isRunning).isFalse

        assertThrows(IllegalStateException::class.java) {
            victim.establishStream(Observable.empty()).blockingFirst()
        }
    }

    @Test
    fun `trying to open used port will cause retry attempts every 5 seconds`() {
        val cfg = SerialPortConfig("FOO", 9600, 8, Parity.NONE, StopBits.STOPBITS_1)
        var count: Int = 0

        serialPortFactory = {
            ++count
            throw PortInUseException()
        }

        val victim = SerialPortDriverImpl(cfg, scheduler!!, serialPortFactory!!, writeCounter!!, readCounter!!)

        scheduler!!.advanceTimeBy(59L, TimeUnit.SECONDS)

        assertThat(victim.isRunning).isFalse
        assertThat(count).isEqualTo( 60 / 5)
    }

    @Test
    fun `serial port with valid parameters is properly configured`() {
        val cfg = SerialPortConfig("FOO", 9600, 8, Parity.NONE, StopBits.STOPBITS_1)

        val commPort = mockk<RXTXPort>(relaxed = true)

        every { commPort.setSerialPortParams(9600, 8, StopBits.STOPBITS_1.value, Parity.NONE.value) } returns Unit

        serialPortFactory = {
            commPort
        }

        val victim = SerialPortDriverImpl(cfg, scheduler!!, serialPortFactory!!, writeCounter!!, readCounter!!)

        verify { commPort.setSerialPortParams(9600, 8, StopBits.STOPBITS_1.value, Parity.NONE.value) }
        verify { commPort.inputStream }
        verify { commPort.outputStream }
        verify { commPort.notifyOnDataAvailable(true) }
        verify { commPort.addEventListener(any()) }
        confirmVerified(commPort)

        assertThat(victim.isRunning).isTrue
        victim.dispose()
        assertThat(victim.isRunning).isFalse
    }

    @Test
    fun `data send commands are enqueued and return in the right order`() {
        val cfg = SerialPortConfig("FOO", 9600, 8, Parity.NONE, StopBits.STOPBITS_1)

        val commPort = mockk<RXTXPort>(relaxed = true)

        every { commPort.setSerialPortParams(9600, 8, StopBits.STOPBITS_1.value, Parity.NONE.value) } returns Unit

        val outStream = ByteArrayOutputStream()
        val buffer = ByteArray(3)
        val inStream = ByteArrayInputStream(buffer)

        serialPortFactory = {
            commPort
         }

        var dataReadyCallback: SerialPortEventListener? = null

        every { commPort.outputStream } returns outStream
        every { commPort.inputStream } returns inStream
        every { commPort.addEventListener(any()) } answers {
            dataReadyCallback = it.invocation.args[0] as SerialPortEventListener
        }

        val victim = SerialPortDriverImpl(cfg, scheduler!!, serialPortFactory!!, writeCounter!!, readCounter!!)

        assertThat(victim.isRunning).isTrue

        val result = mutableListOf<Byte>()
        for (i in 0..2) buffer[i] = i.toByte()
        victim.establishStream(Observable.just(buffer)).take(3).subscribe(result::add)
        scheduler!!.advanceTimeBy(1L, TimeUnit.MINUTES)
        dataReadyCallback!!.serialEvent(SerialPortEvent(commPort, SerialPortEvent.DATA_AVAILABLE, false, false))
        scheduler!!.advanceTimeBy(1L, TimeUnit.MINUTES)

        inStream.reset()
        for (i in 0..2) buffer[i] = i.plus(10).toByte()
        victim.establishStream(Observable.just(buffer)).take(3).subscribe(result::add)
        scheduler!!.advanceTimeBy(1L, TimeUnit.MINUTES)
        dataReadyCallback!!.serialEvent(SerialPortEvent(commPort, SerialPortEvent.DATA_AVAILABLE, false, false))
        scheduler!!.advanceTimeBy(1L, TimeUnit.MINUTES)

        assertThat(result.size).isEqualTo(6)
        assertThat(result).containsExactly(0, 1, 2, 10, 11, 12)


        victim.dispose()
        assertThat(victim.isRunning).isFalse
    }
}