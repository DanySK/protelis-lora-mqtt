package it.unibo.protelis.lora.backbone

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import it.unibo.protelis.lora.DownlinkSerializer
import it.unibo.protelis.lora.Inbound
import it.unibo.protelis.lora.LoRaDeviceUID
import it.unibo.protelis.lora.Message.Companion.toMessage
import it.unibo.protelis.lora.Outbound
import it.unibo.protelis.lora.Payload
import it.unibo.protelis.lora.Segmenter
import it.unibo.protelis.lora.ThreeBytesSegmenter
import it.unibo.protelis.lora.toHex
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.concurrent.TimeUnit

class ProtelisLoRaBackbone @JvmOverloads constructor(
    val applicationName: String,
    timeout: Long = 60,
    timeunit: TimeUnit = TimeUnit.SECONDS,
    private val client: MqttClient = MqttClient("tcp://localhost:1883", ProtelisLoRaBackbone::class.simpleName, MemoryPersistence()),
    private val segmenter: Segmenter = ThreeBytesSegmenter,
    private val serializer: DownlinkSerializer,
    val neighborhoods: NeighborhoodManager
) {
    private val messages: Cache<LoRaDeviceUID, ByteArray> = CacheBuilder.newBuilder()
        .expireAfterWrite(timeout, timeunit)
        .build()
    private var outboundTransactions: Map<LoRaDeviceUID, List<ByteArray>> = emptyMap()
    private data class Counters(val transaction: Int, val frameCount: Int, val frame: Int = 0)
    private var outboundCounters: Map<LoRaDeviceUID, Counters> = emptyMap()
    private val inboundTransactions: Table<LoRaDeviceUID, Int, Map<Int, ByteArray>> = HashBasedTable.create()
//    private var ongoingDownlinks: Map<LoRaDeviceUID> = emptySet()
    fun run(): Unit = client.subscribe("application/$applicationName/node/*/rx") { topic, message ->
        println("Received: $message")
        try {
            val received = message.toMessage<Inbound>()
            val sender = LoRaDeviceUID(received.devEUI.toHex())
            neighborhoods.deviceOnline(sender)
            val packet = received.`object`
            // TODO: deal with empty packets
            val inboundTransaction: Int = packet.transaction
            val frameNumber: Int = packet.frame
            val frameCount: Int = packet.frameCount
            val payload: ByteArray = packet.payload.map { it.toByte() }.toByteArray()
            var thisTransaction = inboundTransactions.get(sender, inboundTransaction) ?: emptyMap()
            thisTransaction += frameNumber to payload
            if (thisTransaction.size == frameCount - 1) {
                // Transaction complete
                messages.put(sender, (0.toByte() until frameCount)
                    .map { thisTransaction.get(it) }
                    .fold(byteArrayOf()) { a, b -> a + (b ?: throw IllegalStateException("Missing data")) }
                )
                inboundTransactions.remove(sender, inboundTransaction)
            } else {
                inboundTransactions.put(sender, inboundTransaction, thisTransaction)
            }
            val toSend: List<ByteArray> = outboundTransactions.get(sender) ?:
                segmenter.segment(serializer.serialize(messages.asMap().filterKeys { it != sender }).toByteArray())
                    .also {
                        outboundTransactions += sender to it
                        // Begin a new transaction
                        val currentCount: Counters = outboundCounters.get(sender)?.let { previous ->
                            Counters(previous.transaction + 1, it.size, 0)
                        } ?: Counters(0, it.size, 0)
                        outboundCounters += sender to currentCount
                    }
            var counters: Counters = outboundCounters.get(sender) ?: throw IllegalStateException()
            if (toSend.size <= 1) {
                outboundTransactions -= sender
            }
            val next = toSend[0]
            client.publish("application/1/node/$sender/tx", Outbound(Payload(
                transaction = counters.transaction,
                frameCount = counters.frameCount,
                frame = counters.frame,
                payload = next
            )).toMqtt())
            // Frame sent, increase for next frame to be sent
            outboundCounters += sender to Counters(counters.transaction, counters.frameCount, counters.frame + 1)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    fun close() = client.close()
}

