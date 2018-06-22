package it.unibo.protelis.lora

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.util.UUID

data class RxInfo(
    val mac: String,
    val rssi: Double,
    val loRaSNR: Double,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double
)

data class DataRate(
    val modulation: String,
    val bandwidth: Double,
    val spreadFactor: Double
)

data class TxInfo(
        val frequency: Double,
        val dataRate: DataRate,
        val adr: Boolean,
        val codeRate: String
)

sealed class Message {
    fun toMqtt(): MqttMessage = MqttMessage(gson.toJson(this).toByteArray(Charsets.US_ASCII))
    companion object {
        protected val gson: Gson = GsonBuilder().create()
        inline fun <reified T: Message> MqttMessage.toMessage(): T = gson.fromJson(toString(), T::class.java)
    }
}

data class Payload (
        val transaction: Int = 0,
        val frameCount: Int = 1,
        val frame: Int = 0,
        val payload: ByteArray = ByteArray(0)
) {
    init {
        if (payload.any { (it < 0) or (it > 255) } )
            throw IllegalArgumentException("Invalid byte payload [${payload.joinToString()}]")
    }
}

data class Inbound (
        val applicationId: String,
        val applicationName: String,
        val deviceName: String,
        val devEUI: String,
        val rxInfo: List<RxInfo>,
        val txInfo: TxInfo,
        val fCnt: Int,
        val fPort: Int,
        val data: String?,
        val `object`: Payload
) : Message()

data class Outbound (
        val `object`: Payload,
        val reference: String = UUID.randomUUID().toString(),
        val confirmed: Boolean = true,
        val fPort: Int = (Math.random() * (Byte.MAX_VALUE - 10)).toInt(),
        val data: String = ""
) : Message()

