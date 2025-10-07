package com.gcs.gRPCModbusAdapter.config

import com.gcs.gRPCModbusAdapter.devices.DeviceFunction
import com.gcs.gRPCModbusAdapter.validation.BaudRateConstraint
import com.gcs.gRPCModbusAdapter.validation.DataBitsConstraint
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.validation.annotation.Validated
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty

enum class Parity(val value: Int) {
    NONE(0),
    ODD(1),
    EVEN(2),
    MARK(3),
    SPACE(4);
}

enum class StopBits(val value: Int) {
    STOPBITS_1(1),
    STOPBITS_2(2),
    STOPBITS_1_5(3);
}


@Validated
@ConfigurationProperties(prefix = "ports")
data class Ports(val entries: List<SerialPortConfig>)

@Validated
@ConfigurationProperties(prefix = "devices")
data class Devices(val entries: List<DeviceConfig>)

@Validated
data class SerialPortConfig (
    @NotEmpty
    val name: String,

    @BaudRateConstraint
    val baudRate: Int,

    @DataBitsConstraint
    val dataBits: Int,

    val parity: Parity,

    val stopBits: StopBits,

    @DefaultValue("2000") val responseWaitTimeMillis: Int,
)

@Validated
data class DeviceConfig(
    @Min(1)
    val id: Byte,

    @NotEmpty
    val name: String,

    @NotEmpty
    val serialPort: String,

    @NotEmpty
    @Min(1)
    val deviceFunctions: Set<DeviceFunction>
)
