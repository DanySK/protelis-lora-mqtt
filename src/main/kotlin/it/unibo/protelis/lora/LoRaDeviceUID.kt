package it.unibo.protelis.lora

import org.protelis.lang.datatype.impl.StringUID

data class LoRaDeviceUID(val devaddr: String) : StringUID(devaddr.forceSize(16))