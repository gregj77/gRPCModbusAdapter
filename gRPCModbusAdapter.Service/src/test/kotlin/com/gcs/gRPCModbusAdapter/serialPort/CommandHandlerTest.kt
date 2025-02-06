package com.gcs.gRPCModbusAdapter.serialPort

import com.gcs.gRPCModbusAdapter.config.Parity
import com.gcs.gRPCModbusAdapter.config.SerialPortConfig
import com.gcs.gRPCModbusAdapter.config.StopBits
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.assertThrows
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

class CommandHandlerTest {
    private val meterRegistry = SimpleMeterRegistry()
    private val factory = CommandHandlerFactory(
        SerialPortConfig("com1", 100, 8, Parity.EVEN, StopBits.STOPBITS_1, 1_000),
        meterRegistry.counter("writeCounter"),
        meterRegistry.counter("readCounter"))

    @BeforeEach
    fun setUp() {
    }

    @AfterEach
    fun tearDown() {
    }

    @Test
    fun `command will timeout if no data is available`() {
        val inputStream = mockk<InputStream>()
        val outputStream = mockk<OutputStream>()

        val victim = factory.createCommandHandler(inputStream, outputStream)
        val bytes = ByteArray(3) { (it + 1).toByte() }
        val completedCallback = CompletableFuture<List<Byte>>()

        every { inputStream.available() } returns 0
        every { outputStream.write(any<ByteArray>()) } just Runs

        victim.handleCommandLoop(1, bytes, completedCallback)

        val err = assertThrows<ExecutionException> {
            completedCallback.get()
        }
        assertThat(err.cause).isExactlyInstanceOf(TimeoutException::class.java)
    }

    @Test
    fun `failure in stream processing propagates to the callback`() {
        val inputStream = mockk<InputStream>()
        val outputStream = mockk<OutputStream>()

        val victim = factory.createCommandHandler(inputStream, outputStream)
        val bytes = ByteArray(3) { (it + 1).toByte() }
        val completedCallback = CompletableFuture<List<Byte>>()

        every { inputStream.available() } returns 0
        every { outputStream.write(any<ByteArray>()) } throws InterruptedException("not possible!")

        victim.handleCommandLoop(1, bytes, completedCallback)

        val err = assertThrows<ExecutionException> {
            completedCallback.get()
        }
        assertThat(err.cause).isExactlyInstanceOf(InterruptedException::class.java)
    }

    @Test
    fun `data will be read from stream when data ready notification is raised`() {
        val inputStream = mockk<InputStream>()
        val outputStream = mockk<OutputStream>()

        val victim = factory.createCommandHandler(inputStream, outputStream)
        val bytes = ByteArray(3) { (it + 1).toByte() }
        val completedCallback = CompletableFuture<List<Byte>>()

        every { inputStream.available() } returns 0 andThen(3) andThen(0)

        every { outputStream.write(any<ByteArray>()) } answers {
            CompletableFuture.runAsync{
                Thread.sleep(200L)
                victim.notifyNewDataAvailable(newValue = true, oldValue = false)
            }
        }

        val slot = CapturingSlot<ByteArray>()
        every { inputStream.read(capture(slot), eq(0), any()) } answers {
            slot.captured[0] = 0xff.toByte()
            slot.captured[1] = 0xfe.toByte()
            slot.captured[2] = 0xfd.toByte()
            3
        }

        victim.handleCommandLoop(1, bytes, completedCallback)

        val result = completedCallback.get()
        assertThat(result)
            .isNotNull
            .hasSize(3)
            .containsExactly(0xff.toByte(), 0xfe.toByte(), 0xfd.toByte())
    }

    @Test
    fun `data read in chunks will be properly concatenated`() {
        val inputStream = mockk<InputStream>()
        val outputStream = mockk<OutputStream>()

        val victim = factory.createCommandHandler(inputStream, outputStream)
        val bytes = ByteArray(3) { (it + 1).toByte() }
        val completedCallback = CompletableFuture<List<Byte>>()

        every { inputStream.available() } returns 0 andThen(1) andThen(1) andThen (1) andThen(0)

        every { outputStream.write(any<ByteArray>()) } answers {
            CompletableFuture.runAsync{
                Thread.sleep(200L)
                victim.notifyNewDataAvailable(newValue = true, oldValue = false)
            }
        }

        val buffer = CapturingSlot<ByteArray>()
        val offset = CapturingSlot<Int>()
        val size = CapturingSlot<Int>()
        val response = AtomicInteger(255)
        every { inputStream.read(capture(buffer), capture(offset), capture(size)) } answers {
            buffer.captured[offset.captured] = response.getAndDecrement().toByte()
            1
        }

        victim.handleCommandLoop(1, bytes, completedCallback)

        val result = completedCallback.get()
        assertThat(result)
            .isNotNull
            .hasSize(3)
            .containsExactly(0xff.toByte(), 0xfe.toByte(), 0xfd.toByte())
    }
}