package it.unibo.protelis.lora.backbone

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import it.unibo.protelis.lora.Inbound
import it.unibo.protelis.lora.LoRaDeviceUID
import it.unibo.protelis.lora.Message.Companion.toMessage
import it.unibo.protelis.lora.Segmenter
import it.unibo.protelis.lora.ThreeBytesSegmenter
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.concurrent.TimeUnit

class ProtelisLoRaBackbone @JvmOverloads constructor(
    val applicationName: String,
    timeout: Long = 60,
    timeunit: TimeUnit = TimeUnit.SECONDS,
    private val client: MqttClient = MqttClient("tcp://localhost:1883", ProtelisLoRaBackbone::class.simpleName, MemoryPersistence()),
    private val segmenter: Segmenter = ThreeBytesSegmenter,
    val neighborhoods: NeighborhoodManager
) {
    private val messages: Cache<LoRaDeviceUID, ByteArray> = CacheBuilder.newBuilder()
        .expireAfterWrite(timeout, timeunit)
        .build()
    private var outboundTransactions: Map<LoRaDeviceUID, Byte> = emptyMap()
    private val inboundTransactions: Table<LoRaDeviceUID, Int, Map<Int, ByteArray>> = HashBasedTable.create()
    fun run(): Unit = client.subscribe("application/$applicationName/node/*/rx") { topic, message ->
        println("Received: $message")
        try {
            val received = message.toMessage<Inbound>()
            val sender = LoRaDeviceUID(received.devEUI)
            neighborhoods.deviceOnline(sender)
            val packet = received.`object`
            val transaction: Int = packet.transaction
            val frameNumber: Int = packet.frame
            val frameCount: Int = packet.frameCount
            val payload: ByteArray = packet.payload.map { it.toByte() }.toByteArray()
            var thisTransaction = inboundTransactions.get(sender, transaction) ?: emptyMap()
            thisTransaction += frameNumber to payload
            if (thisTransaction.size == frameCount) {
                // Transaction complete
                messages.put(sender, (0.toByte() until frameCount)
                    .map { thisTransaction.get(it) }
                    .fold(byteArrayOf()) { a, b -> a + (b ?: throw IllegalStateException("Missing data")) }
                )
                inboundTransactions.remove(sender, transaction)
            } else {
                inboundTransactions.put(sender, transaction, thisTransaction)
            }
            // TODO: create answer and send it
//            sampleClient.publish("application/1/node/003fb1fe08f0f264/tx", toSend)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    fun close() = client.close()
}

