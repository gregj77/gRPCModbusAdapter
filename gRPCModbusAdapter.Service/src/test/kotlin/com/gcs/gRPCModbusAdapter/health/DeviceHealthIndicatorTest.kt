package com.gcs.gRPCModbusAdapter.health

import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.ReactiveHealthContributor

internal class DeviceHealthIndicatorTest {

    val victim = DeviceHealthIndicator(mapOf("foo" to fooDevice, "bar" to barDevice))

    @Test
    fun iteratorReturnsAllContributors() {

        val indicators = victim.iterator().asSequence().toSet()
        assertThat(indicators).hasSize(2)

        assertThat(indicators.first { it.name == "foo"}.contributor).isEqualTo(fooDevice)
        assertThat(indicators.first { it.name == "bar"}.contributor).isEqualTo(barDevice)
    }

    @Test
    fun getContributorReturnsValidInstance() {
        assertThat(victim.getContributor("foo")).isEqualTo(fooDevice)
        assertThat(victim.getContributor("bar")).isEqualTo(barDevice)
    }

    companion object {
        val fooDevice = mockk<ReactiveHealthContributor>()
        val barDevice = mockk<ReactiveHealthContributor>()
    }
}