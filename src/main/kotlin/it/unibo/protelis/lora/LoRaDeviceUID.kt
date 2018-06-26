package it.unibo.protelis.lora

import org.protelis.lang.datatype.impl.StringUID

data class LoRaDeviceUID(val deviceAddress: HexString) : StringUID(deviceAddress.asString) {
    companion object {
        val deviceAddressLength = 16
    }
}