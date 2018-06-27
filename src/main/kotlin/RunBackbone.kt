
import com.fazecast.jSerialComm.SerialPort
import it.unibo.protelis.lora.Identity
import it.unibo.protelis.lora.backbone.ProtelisLoRaBackbone
import it.unibo.protelis.lora.client.Command
import it.unibo.protelis.lora.client.LoRaExecutionContext
import it.unibo.protelis.lora.client.LoRaNetworkManager
import it.unibo.protelis.lora.client.RN2483
import it.unibo.protelis.lora.client.purge
import org.protelis.lang.ProtelisLoader
import org.protelis.vm.ProtelisVM

fun main(args: Array<String>) {
    val appName = "1"
    val portName = "/dev/ttyACM1"
    ProtelisLoRaBackbone(appName).run()
    val port = SerialPort.getCommPort(portName)
    // Cleanup messages left on the port from previous executions:
    println("Purging port: ${port.purge()}")
    val device = RN2483(port)
    device.execute(Command.Mac.Set.DataRate(5))
    val networkManager = LoRaNetworkManager(port, 300, Identity)
    val context = LoRaExecutionContext(device, networkManager = networkManager)
    val vm = ProtelisVM(ProtelisLoader.parse("1"), context)
    while (true) {
        vm.runCycle()
        Thread.sleep(60000)
    }
}