package com.gcs.gRPCModbusAdapter.serialPort

import com.gcs.gRPCModbusAdapter.config.Parity
import com.gcs.gRPCModbusAdapter.config.SerialPortConfig
import com.gcs.gRPCModbusAdapter.config.StopBits
import gnu.io.PortInUseException
import gnu.io.RXTXPort
import gnu.io.SerialPortEvent
import gnu.io.SerialPortEventListener
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.Status
import reactor.core.scheduler.Schedulers
import reactor.test.scheduler.VirtualTimeScheduler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Duration

internal class SerialPortDriverImplTest {

    val cfg = SerialPortConfig("FOO", 9600, 8, Parity.NONE, StopBits.STOPBITS_1, 2_000)
    var serialPortFactory: ((String) -> RXTXPort)? = null
    var serialPortMock: RXTXPort? = null
    var scheduler: VirtualTimeScheduler? = null
    var writeCounter: Counter? = null
    var readCounter: Counter? = null
    var commandHandlerFactory: CommandHandlerFactory? = null

    @BeforeEach
    fun setUp() {
        scheduler = VirtualTimeScheduler.create()
        serialPortFactory = { serialPortMock!! }
        val registry = SimpleMeterRegistry()
        writeCounter = registry.counter("bytesWritten")
        readCounter = registry.counter("bytesRead")
        commandHandlerFactory = CommandHandlerFactory(cfg, writeCounter!!, readCounter!!)
    }

    @AfterEach
    fun tearDown() {
        serialPortFactory = null
    }

    @Test
    fun `trying to create a non existing port results in an error when trying to submit data`() {
        serialPortFactory = { throw IllegalArgumentException("no such port $it")}

        val victim = SerialPortDriverImpl(cfg, Schedulers.single(), serialPortFactory!!, {}, commandHandlerFactory!!)

        assertThat(victim.isRunning).isFalse

        assertThrows(IllegalStateException::class.java) {
            victim.communicateAsync(ByteArray(0)).blockFirst()
        }
    }

    @Test
    fun `trying to open used port will cause retry attempts every 5 seconds`() {
        var count: Int = 0

        serialPortFactory = {
            ++count
            throw PortInUseException()
        }

        val victim = SerialPortDriverImpl(cfg, scheduler!!, serialPortFactory!!, {}, commandHandlerFactory!!)

        scheduler!!.advanceTimeBy(Duration.ofSeconds(59L))

        assertThat(victim.isRunning).isFalse
        assertThat(count).isEqualTo( 60 / 5)

        val health = victim.health()
        assertThat(health.status).isEqualTo(Status.OUT_OF_SERVICE)
    }

    @Test
    fun `serial port with valid parameters is properly configured`() {
        val commPort = mockk<RXTXPort>(relaxed = true)

        every { commPort.setSerialPortParams(9600, 8, StopBits.STOPBITS_1.value, Parity.NONE.value) } returns Unit

        serialPortFactory = {
            commPort
        }

        val victim = SerialPortDriverImpl(cfg, scheduler!!, serialPortFactory!!, {}, commandHandlerFactory!!)

        verify { commPort.setSerialPortParams(9600, 8, StopBits.STOPBITS_1.value, Parity.NONE.value) }
        verify { commPort.inputStream }
        verify { commPort.outputStream }
        verify { commPort.notifyOnDataAvailable(true) }
        verify { commPort.addEventListener(any()) }
        confirmVerified(commPort)

        assertThat(victim.isRunning).isTrue
        val upHealth = victim.health()
        assertThat(upHealth.status).isEqualTo(Status.UP)

        victim.dispose()
        assertThat(victim.isRunning).isFalse
        val disposedHealth = victim.health()
        assertThat(disposedHealth.status).isEqualTo(Status.DOWN)

    }

    @Test
    fun `data send commands are enqueued and return in the right order`() {
        val commPort = mockk<RXTXPort>(relaxed = true)

        every { commPort.setSerialPortParams(9600, 8, StopBits.STOPBITS_1.value, Parity.NONE.value) } returns Unit

        val outStream = ByteArrayOutputStream()
        val buffer = ByteArray(3)
        val inStream = object  : ByteArrayInputStream(buffer) {
            override fun available(): Int {
                return super.available()
            }
        }

        serialPortFactory = {
            commPort
         }

        var dataReadyCallback: SerialPortEventListener? = null

        every { commPort.outputStream } returns outStream
        every { commPort.inputStream } returns inStream
        every { commPort.addEventListener(any()) } answers {
            dataReadyCallback = it.invocation.args[0] as SerialPortEventListener
        }

        val victim = SerialPortDriverImpl(cfg, Schedulers.parallel(), serialPortFactory!!, {}, commandHandlerFactory!!)

        assertThat(victim.isRunning).isTrue

        val result = mutableListOf<Byte>()
        for (i in 0..2) buffer[i] = i.toByte()
        victim.communicateAsync(buffer).take(3).subscribe(result::add)

        Thread.sleep(1_000L)
        inStream.reset()

        dataReadyCallback!!.serialEvent(SerialPortEvent(commPort, SerialPortEvent.DATA_AVAILABLE, false, true))
        Thread.sleep(500L)

        for (i in 0..2) buffer[i] = i.plus(10).toByte()
        victim.communicateAsync(buffer).take(3).subscribe(result::add)
        Thread.sleep(1_000L)

        inStream.reset()
        dataReadyCallback!!.serialEvent(SerialPortEvent(commPort, SerialPortEvent.DATA_AVAILABLE, false, true))
        Thread.sleep(500L)

        assertThat(result.size).isEqualTo(6)
        assertThat(result).containsExactly(0, 1, 2, 10, 11, 12)

        victim.dispose()
        assertThat(victim.isRunning).isFalse
    }
}