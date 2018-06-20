package it.unibo.protelis.lora.client

import com.fazecast.jSerialComm.SerialPort
import writeString
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor

sealed class Command(val command: String, val expectsResponse: Boolean = true) {
    open fun response(response: String): Response =
        if (expectsResponse) Response.get(response)
        else throw IllegalStateException("$command does not provide a response.")
    override fun toString() = command

    sealed class System(val subcommand: String, expectsResponse: Boolean = true)
            : Command("sys $subcommand", expectsResponse) {
        class Sleep(length: Int) : System("sleep $length") {
            init {
                if (!(length in 100..429967296)) {
                    throw IllegalArgumentException("invalid length")
                }
            }
        }
        object Reset: System("reset")
        object EraseFirmware: System("eraseFW", false)
        object FactoryReset: System("factoryRESET")
        sealed class Set(subcommand: String) : System("set $subcommand") {
            class NVM(address: String, data: String): Set("nvm $address $data") {
                init {
                    if (!address.isHex()) throw IllegalArgumentException("address must be hex: $address")
                    if (!data.isHex()) throw IllegalArgumentException("address must be hex: $address")
                }
            }
            class PinDig(name: String, state: Boolean): Set("pindig $name ${if (state) 1 else 0}"){
                companion object {
                    val availablePins = (0..14).map { "GPI0$it" }.toSet() + setOf("UART_CTS", "UART_RTS", "TEST0", "TEST1")
                }
                init {
                    if (!(name in availablePins)) throw IllegalArgumentException("Pin name ($name) must be in $availablePins")
                }
            }
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
    fun execute(command: Command): Response = with(command) {
        port.writeString(this.command)
        if (command.expectsResponse) response(port.readLine()) else Response.NoResponse
    }
}

fun String.isHex() = "[a-f0-9]*".toRegex().matches(this)

fun main(args: Array<String>) {
    println((Command.System.Reset.response("RN2483")))
}