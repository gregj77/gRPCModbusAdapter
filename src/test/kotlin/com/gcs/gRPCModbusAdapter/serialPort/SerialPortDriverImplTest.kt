package com.gcs.gRPCModbusAdapter.serialPort

import com.gcs.gRPCModbusAdapter.config.Parity
import com.gcs.gRPCModbusAdapter.config.SerialPortConfig
import com.gcs.gRPCModbusAdapter.config.StopBits
import gnu.io.PortInUseException
import gnu.io.RXTXPort
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.schedulers.TestScheduler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.mockito.kotlin.mock
import java.util.concurrent.TimeUnit

internal class SerialPortDriverImplTest {

    var serialPortFactory: ((String) -> RXTXPort)? = null
    var serialPortMock: RXTXPort? = null
    var scheduler: TestScheduler? = null

    @BeforeEach
    fun setUp() {
        scheduler = TestScheduler()
        serialPortFactory = { serialPortMock!! }
    }

    @AfterEach
    fun tearDown() {
        serialPortFactory = null
    }

    @Test
    fun `trying to create a non existing port results in an error when trying to submit data`() {
        val cfg = SerialPortConfig("FOO", 9800, 9, Parity.NONE, StopBits.STOPBITS_1)

        serialPortFactory = { throw IllegalArgumentException("no such port $it")}

        val victim = SerialPortDriverImpl(cfg, Schedulers.io(), serialPortFactory!!)

        assertThat(victim.isRunning).isFalse

        assertThrows(IllegalStateException::class.java) {
            victim.establishStream(Observable.empty()).blockingFirst()
        }
    }

    @Test
    fun `trying to open used port will cause retry attempts every 5 seconds`() {
        val cfg = SerialPortConfig("FOO", 9800, 9, Parity.NONE, StopBits.STOPBITS_1)
        var count: Int = 0

        serialPortFactory = {
            ++count
            throw PortInUseException()
        }

        val victim = SerialPortDriverImpl(cfg, scheduler!!, serialPortFactory!!)

        scheduler!!.advanceTimeBy(59L, TimeUnit.SECONDS)

        assertThat(victim.isRunning).isFalse
        assertThat(count).isEqualTo( 60 / 5)
    }
}