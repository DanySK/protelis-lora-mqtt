package it.unibo.protelis.lora.client

import it.unibo.protelis.lora.Segmenter
import org.apache.commons.codec.binary.Hex
import org.protelis.lang.datatype.DeviceUID

class LoRaClassANetworking(protected val node: RN2483, protected val segmenter: Segmenter) {

    private var transaction: Byte = 0

    fun send(payload: ByteArray): Map<DeviceUID, ByteArray> {
        var received: ByteArray = ByteArray(0)
        segmenter.segment(payload, transactionId = transaction++).forEach {
            val hex = Hex.encodeHexString(it)
            println("Sending $hex")
            val response = node.execute(Command.Mac.Transmit(node, hex))
            when (response) {
                is Response.Ok -> when(response.then) {
//                    is Response.DataReceived -> received += response.then.data
                }
            }
        }
        TODO()
    }

}