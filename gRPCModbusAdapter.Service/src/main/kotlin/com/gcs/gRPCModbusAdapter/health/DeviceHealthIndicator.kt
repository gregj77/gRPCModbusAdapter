package com.gcs.gRPCModbusAdapter.health

import com.gcs.gRPCModbusAdapter.config.DeviceManagementConfig.Companion.DeviceHealthContributor
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.actuate.health.CompositeReactiveHealthContributor
import org.springframework.boot.actuate.health.NamedContributor
import org.springframework.boot.actuate.health.ReactiveHealthContributor
import org.springframework.stereotype.Component

@Component("modbusDevices")
class DeviceHealthIndicator(@Qualifier(DeviceHealthContributor) private val healthContributors: Map<String, ReactiveHealthContributor>) : CompositeReactiveHealthContributor {

    override fun iterator(): MutableIterator<NamedContributor<ReactiveHealthContributor>> = healthContributors
        .map { NamedContributor.of(it.key, it.value) }
        .toMutableList()
        .iterator()

    override fun getContributor(name: String?): ReactiveHealthContributor = healthContributors[name]!!
}