package it.unibo.protelis.lora.client

import it.unibo.protelis.lora.Segmenter
import org.protelis.lang.datatype.DeviceUID

class LoRaClassANetworking(protected val node: LoRaNode, protected val segmenter: Segmenter) {

    fun send(payload: ByteArray): Map<DeviceUID, ByteArray> {
        TODO()
    }

}