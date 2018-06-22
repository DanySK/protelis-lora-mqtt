package it.unibo.protelis.lora.backbone

import it.unibo.protelis.lora.LoRaDeviceUID

interface NeighborhoodManager {
    fun deviceOnline(device: LoRaDeviceUID)
    fun neighborsOf(device: LoRaDeviceUID): Collection<LoRaDeviceUID>
}

object AllNeighbors: NeighborhoodManager {
    private var devices: Set<out LoRaDeviceUID> = emptySet()
    override fun deviceOnline(device: LoRaDeviceUID) { devices += device }
    override fun neighborsOf(device: LoRaDeviceUID) = devices
}