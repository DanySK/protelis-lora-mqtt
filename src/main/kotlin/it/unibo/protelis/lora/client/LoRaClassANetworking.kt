package it.unibo.protelis.lora.client

import it.unibo.protelis.lora.DownlinkSerializer
import it.unibo.protelis.lora.HexString
import it.unibo.protelis.lora.Segmenter
import it.unibo.protelis.lora.ThreeBytesSegmenter
import it.unibo.protelis.lora.TwoBytesSizedHeader
import org.protelis.lang.datatype.DeviceUID

class LoRaClassANetworking(
    private val node: RN2483,
    private val segmenter: Segmenter = ThreeBytesSegmenter,
    private val downlinkSerializer: DownlinkSerializer = TwoBytesSizedHeader) {

    private var transaction: Byte = 0

    fun send(payload: ByteArray): Map<out DeviceUID, ByteArray> {
        var received: List<ByteArray> = emptyList()
        segmenter.segment(payload, transactionId = transaction++).forEach {
            val response = sendAndReceive(HexString(it))
            response?.also {
                if (segmenter.transactionComplete(received)) {
                    throw IllegalStateException("Unexpected response: $it, transaction was complete.")
                }
                received += it
            }
        }
        while (!segmenter.transactionComplete(received)) {
            received += sendAndReceive(HexString("")) ?: throw IllegalStateException("Missing response data")
        }
        return downlinkSerializer.deserialize(HexString(segmenter.assemble(received)))
    }

    private fun sendAndReceive(payload: HexString): ByteArray? {
        println("Sending ${payload}")
        val response = node.execute(Command.Mac.Transmit(node, payload))
        return when (response) {
            is Response.Ok -> response.then ?.let {
                when(it) {
                    is Response.DataReceived -> HexString(it.data).toByteArray()
                    else -> throw IllegalStateException("Unexpected response: $response")
                }
            }
            else -> throw IllegalStateException("Unexpected response: $response")
        }
    }

}