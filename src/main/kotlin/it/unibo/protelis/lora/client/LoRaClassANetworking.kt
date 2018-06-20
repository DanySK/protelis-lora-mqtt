package it.unibo.protelis.lora.client

import it.unibo.protelis.lora.Segmenter
import org.protelis.lang.datatype.DeviceUID

class LoRaClassANetworking(protected val node: LoRaNode, protected val segmenter: Segmenter) {

    private var transaction: Byte = 0

    fun send(payload: ByteArray): Map<DeviceUID, ByteArray> {
        segmenter.segment(payload, transactionId = transaction++).forEach {

        }
        TODO()
    }

}