
import it.unibo.protelis.lora.Inbound
import it.unibo.protelis.lora.Message.Companion.toMessage
import it.unibo.protelis.lora.Outbound
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

fun main(args: Array<String>) {
    val sampleClient = MqttClient("tcp://localhost:1883", "example", MemoryPersistence())
    sampleClient.connect(MqttConnectOptions().also { it.isCleanSession = true })
    println("subscribing")
    sampleClient.subscribe("application/1/node/003fb1fe08f0f264/rx") { topic, message: MqttMessage ->
        println("Received: $message")
        try {
            val received = message.toMessage<Inbound>()
            println("Converted to $received")
            println("Payload ${received.`object`}")
            // TODO: parse JSON, extract transaction and frame, rebuild byte sequence
            val toSend = Outbound(received.`object`, confirmed = false).toMqtt()
            println("sending $toSend")
            sampleClient.publish("application/1/node/003fb1fe08f0f264/tx", toSend)
            println("Sent: $toSend")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

