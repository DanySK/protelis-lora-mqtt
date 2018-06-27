package it.unibo.protelis.lora.client

import it.unibo.protelis.lora.LoRaDeviceUID
import it.unibo.protelis.lora.forceSize
import it.unibo.protelis.lora.toHex
import org.protelis.lang.datatype.DeviceUID
import org.protelis.lang.datatype.Tuple
import org.protelis.vm.ExecutionEnvironment
import org.protelis.vm.LocalizedDevice
import org.protelis.vm.SpatiallyEmbeddedDevice
import org.protelis.vm.TimeAwareDevice
import org.protelis.vm.impl.AbstractExecutionContext
import org.protelis.vm.impl.SimpleExecutionEnvironment

class LoRaExecutionContext<P : Any>(
        val device: RN2483,
        executionEnvironment: ExecutionEnvironment = SimpleExecutionEnvironment(),
        networkManager: LoRaNetworkManager<P>)
    : AbstractExecutionContext(executionEnvironment, networkManager), TimeAwareDevice, SpatiallyEmbeddedDevice, LocalizedDevice {

    override fun nbrRange() = buildField({it}, 1)

    override fun getCoordinates(): Tuple {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun nbrVector() = TODO()

    override fun getDeltaTime(): Number {
        return super.getDeltaTime()
    }

    override fun getExecutionEnvironment(): ExecutionEnvironment {
        return super.getExecutionEnvironment()
    }

    override fun getCurrentTime() = System.currentTimeMillis()

    override fun instance() = LoRaExecutionContext(device, executionEnvironment, this.networkManager as LoRaNetworkManager<P>)

    override fun nbrDelay() = buildField( {it}, deltaTime)

    override fun nbrLag() = buildField({ currentTime - it }, currentTime)

    private var deviceUID: LoRaDeviceUID = lazy {
        val eui = device.execute(Command.Mac.Get.DeviceEUI)
        println(eui)
        LoRaDeviceUID(
            when (eui) {
                is Response.Value -> eui.value.forceSize(16).toHex()
                else -> throw IllegalStateException("Unexpected response $eui")
            })
    }.value

    override fun getDeviceUID(): DeviceUID = deviceUID

    override fun nextRandomDouble() = Math.random()
}