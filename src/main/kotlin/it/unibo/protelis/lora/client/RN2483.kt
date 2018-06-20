package it.unibo.protelis.lora.client

import com.fazecast.jSerialComm.SerialPort
import writeString
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor

sealed class Command(val command: String) {
    abstract fun response(response: String): Response

    sealed class System(subcommand: String) : Command("sys $subcommand") {
        class Sleep(length: Int) : System("sleep $length") {
            init {
                if (!(length in 100..429967296)) {
                    throw IllegalArgumentException("invalid length")
                }
            }
            override fun response(response: String) = Response.get(response).let {
                if (it is Response.Ok || it is Response.InvalidData) it
                else throw IllegalArgumentException("Not a valid response: $response")
            }
        }
        object Reset: System("reset") {
            override fun response(response: String) = throw IllegalStateException("${javaClass.simpleName} has no response")
        }
    }
}

sealed class Response(val preamble: String, val then: Response? = null) {
    companion object {
        fun get(key: String) = Response::class.nestedClasses.asSequence()
            .filter { it.isFinal && it.isSubclassOf(Response::class) }
            .map{ it.objectInstance?: it.primaryConstructor?.call(key)}
            .map { it as Response }
            .filter { it.preamble == key.split(" ")[0] }
            .first()
    }
    override fun toString() = "${javaClass.simpleName}($preamble)"

    object Ok : Response("ok", then = null)
    object InvalidParam: Response("invalid_param")
    object NotJoined: Response("not_joined")
    object NoFreeChannel: Response("no_free_ch")
    object Silent: Response("silent")
    object FrameCounterRolledOver: Response("frame_counter_err_rejoin_needed")
    object Busy: Response("busy")
    object Paused: Response("mac_paused")
    object InvalidDataLength: Response("invalid_data_len")
    object TransmissionOKNoReply: Response("mac_tx_ok")
    object Error: Response("mac_err")
    object InvalidData: Response("invalid_data")
    object NoResponse: Response("")
    class TransmissionOKDataReceived(response: String): Response("mac_rx") {
        val port: Int
        val data: String
        init {
            val groups = regex.matchEntire(response)?.groups
            if (groups == null) throw IllegalArgumentException("Cannot parse response: $response")
            val p = groups[0]?.value
            if (p == null) throw IllegalArgumentException("Cannot match port in: $response")
            val d = groups[1]?.value
            if (d == null) throw IllegalArgumentException("Cannot match data in: $response")
            port = Integer.parseInt(p)
            data = d
        }
        companion object {
            private val regex = "mac_rx (?<port>\\d*) (?<data>[a-f0-9]*)".toRegex()
        }
    }
    class DeviceInfo(val info: String) : Response("RN2483")
}

class RN2483(private val port: SerialPort) {
    fun execute(command: Command, shouldRespond: Boolean = true): Response = with(command) {
        port.writeString(this.command)
        if (shouldRespond) response(port.readLine()) else Response.NoResponse
    }
}

fun main(args: Array<String>) {
    println((Response.get("RN2483 sdafjewiu freiubire") as Response.DeviceInfo).info)
}