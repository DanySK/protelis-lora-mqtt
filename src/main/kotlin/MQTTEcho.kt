import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

fun main(args: Array<String>) {
    val sampleClient = MqttClient("tcp://localhost:1883", "example", MemoryPersistence())
    sampleClient.connect(MqttConnectOptions().also { it.isCleanSession = true })
    sampleClient.publish("application/ahmeddanilo/node/[devEUI]/tx", MqttMessage("test".toByteArray(Charsets.UTF_8)))
    sampleClient.disconnect()
}

