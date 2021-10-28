package com.gcs.gRPCModbusAdapter.health

import com.gcs.gRPCModbusAdapter.config.SerialPortManagementConfig.Companion.SerialPortsHealthContributor
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.actuate.health.*
import org.springframework.stereotype.Component

@Component("managedSerialPorts")
class SerialPortHealthIndicator( @Qualifier(SerialPortsHealthContributor) private val healthContributors: Map<String, HealthContributor>) : CompositeHealthContributor {

    override fun iterator(): MutableIterator<NamedContributor<HealthContributor>> = healthContributors
        .map { NamedContributor.of(it.key, it.value) }
        .toMutableList()
        .iterator()

    override fun getContributor(name: String): HealthContributor = healthContributors[name]!!
}