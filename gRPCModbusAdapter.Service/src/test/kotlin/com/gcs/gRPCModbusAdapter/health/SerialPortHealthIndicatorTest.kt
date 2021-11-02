package com.gcs.gRPCModbusAdapter.health

import io.mockk.mockk
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.HealthContributor

internal class SerialPortHealthIndicatorTest {

    val victim = SerialPortHealthIndicator(mapOf("com1" to com1Port, "com2" to com2Port))

    @Test
    fun iteratorReturnsAllContributors() {

        val indicators = victim.iterator().asSequence().toSet()
        Assertions.assertThat(indicators).hasSize(2)

        Assertions.assertThat(indicators.first { it.name == "com1" }.contributor).isEqualTo(com1Port)
        Assertions.assertThat(indicators.first { it.name == "com2" }.contributor).isEqualTo(com2Port)
    }

    @Test
    fun getContributorReturnsValidInstance() {
        Assertions.assertThat(victim.getContributor("com1")).isEqualTo(com1Port)
        Assertions.assertThat(victim.getContributor("com2")).isEqualTo(com2Port)
    }

    companion object {
        val com1Port = mockk<HealthContributor>()
        val com2Port = mockk<HealthContributor>()
    }
}