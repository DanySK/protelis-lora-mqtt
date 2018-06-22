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
            data class NVM(val address: String, val data: String): Set("nvm $address $data") {
                init {
                    if (!address.isHex()) throw IllegalArgumentException("address must be hex: $address")
                    if (!data.isHex()) throw IllegalArgumentException("address must be hex: $address")
                }
            }
            data class PinDig(val name: String, val state: Boolean): Set("pindig $name ${if (state) 1 else 0}"){
                companion object {
                    val availablePins = (0..14).map { "GPI0$it" }.toSet() + setOf("UART_CTS", "UART_RTS", "TEST0", "TEST1")
                }
                init {
                    if (!(name in availablePins)) throw IllegalArgumentException("Pin name ($name) must be in $availablePins")
                }
            }
        }
        sealed class Get(subcommand: String): System("get $subcommand") {
            object Version: Get("ver")
            data class NVM(val address: String): Get("nvm $address") {
                init {
                    if (!address.isHex()) throw IllegalArgumentException("Address must be hex: $address")
                }
            }
            object VDD: Get("vdd")
            object HardwareEUI: Get("hweui")
        }
    }
    sealed class Mac(val subcommand: String) : Command("mac $subcommand") {
        data class Reset(val band: Int) : Mac("reset $band")
        data class Transmit(
            val device: RN2483,
            val data: String,
            val confirmed: Boolean = false,
            val port: Int = (Math.random() * 223).toInt() + 1)
            : Mac("tx ${if (confirmed) "" else "un"}cnf ${port.forceRange(1, 233)} ${data.forceHex()}") {
            override fun response(response: String) = super.response(response).let {
                    if (it is Response.Ok) Response.Ok(super.response(device.port.readLine()))
                    else it
                }
        }
        data class Join(val device: RN2483, val otaa: Boolean = false)
            : Mac("join ${if (otaa) "otaa" else "abp"}") {
            override fun response(response: String) = super.response(response).let {
                if (it is Response.Ok) Response.Ok(super.response(device.port.readLine()))
                else it
            }
        }
        object Save: Mac("save")
        object ForceEnable: Mac("forceENABLE")
        object Pause: Mac("pause")
        object Resume: Mac("resume")
        sealed class Set(subcommand: String): Mac("set $subcommand") {
            data class DeviceAddress(val address: String): Set(address) {
                init { address.forceHex().forceSize(8) }
            }
            data class DeviceEUI(val devEUI: String)
                : Set("deveui ${devEUI.forceHex().forceSize(16)}")
            data class ApplicationEUI(val appEUI: String)
                : Set("appeui ${appEUI.forceHex().forceSize(16)}")
            data class NetworkSessionKey(val networkSessionKey: String)
                : Set("nwkskey ${networkSessionKey.forceHex().forceSize(32)}")
            data class ApplicationSessionKey(val applicationSessionKey: String)
                : Set("appskey ${applicationSessionKey.forceHex().forceSize(32)}") {
            }
            data class ApplicationKey(val applicationKey: String)
                : Set("appkey ${applicationKey.forceHex().forceSize(32)}")
            data class PowerIndex(val index: Int): Set("pwridx ${index.forceRange(0, 5)}")
            data class DataRate(val dataRate: Int): Set("dr ${dataRate.force3bit()}")
            data class AdaptiveDataRate(val status: Boolean = true): Set("adr ${status.toOnOff()}")
            data class BatteryLevel(val level: Int): Set("bat ${level.force8bit()}")
            data class Retransmissions(val retransmissions: Int): Set("retx ${retransmissions.force8bit()}")
            data class LinkCheck(val linkCheck: Int): Set("linkchk ${linkCheck.force16bit()}")
            data class FirstReceiveDelay(val rxDelay: Int): Set("rxdelay1 ${rxDelay.force16bit()}")
            data class SecondReceiveDataRate(val dataRate: Int, val frequency: Int)
                : Set("rx2 ${dataRate.forceRange(0, 7)} ${frequency.forceValidFrequency()}")
            data class AutomaticReply(val status: Boolean = true): Set("ar ${status.toOnOff()}")
            sealed class Channel(subcommand: String): Set("ch $subcommand") {
                data class Frequency(val channelID: Int, val frequency: Int)
                    : Channel("freq ${channelID.forceRange(3, 15)} ${frequency.forceValidFrequency()}")
                data class DutyCycle(val channelID: Int, val dutyCycle: Int)
                    : Channel("dcycle ${channelID.force4bit()} ${dutyCycle.force16bit()}")
                data class OperatingDataRange(val channelID: Int, val minRange: Int, val maxRange: Int)
                    : Channel("drrange ${channelID.force4bit()} ${minRange.force3bit()} ${maxRange.force3bit()}")
                data class Status(val channelID: Int, val status: Boolean)
                    : Channel("status ${channelID.force4bit()} ${status.toOnOff()}")
            }
        }
        sealed class Get(subcommand: String): Mac("get $subcommand"){
            object DeviceAddress: Get("devaddr")
            object DeviceEUI: Get("deveui")
            object ApplicationEUI: Get("appeui")
            object DataRate: Get("dr")
            object Band: Get("band")
            object PowerIndex: Get("pwridx")
            object AdaptiveDataRate: Get("adr")
            object Retransmissions: Get("retx")
            object FirstReceiveDelay: Get("rxdelay1")
            object SecondReceiveDelay: Get("rxdelay2")
            data class SecondReceiveDataRate(val frequency: Int): Get("rx2 ${frequency.forceValidFrequency()}")
            object Prescaler: Get("dcycleps")
            object DemodulationMargin: Get("mrgn")
            object Gateways: Get("gwnb")
            object Status: Get("status")
            sealed class Channel(subcommand: String, val channelID: Int): Get("ch $subcommand ${channelID.force4bit()}") {
                class Frequency(channelID: Int): Channel("freq", channelID)
                class DutyCycle(channelID: Int): Channel("dcycle", channelID)
                class DataRateIndexRange(channelID: Int): Channel("drrange", channelID)
                class Status(channelID: Int): Channel("status", channelID)
            }
        }
    }
}

fun Int.forceValidFrequency(): Int = this.let {
    if (it < 500000000) it.forceRange(433050000, 434790000)
    else it.forceRange(863000000, 870000000)
}

fun Int.force16bit(): Int = this.forceRange(0, 65535)
fun Int.force8bit(): Int = this.forceRange(0, 255)
fun Int.force4bit(): Int = this.forceRange(0, 15)
fun Int.force3bit(): Int = this.forceRange(0, 7)
fun Boolean.toOnOff() = if (this) "on" else "off"

sealed class Response(val preamble: String, val then: Response? = null) {
    companion object {
        fun get(key: String) = Response::class.nestedClasses.asSequence()
            .filter { it.isFinal && it.isSubclassOf(Response::class) }
            .map{ it.objectInstance ?: it.primaryConstructor?.let {
                println("$it - ${it.parameters} - ${it.parameters.all { it.isOptional }}")
                if (it.parameters.size == 0 || it.parameters.all { it.isOptional }) it.callBy(emptyMap())
                else it.call(key)
            }}
            .map { it as Response }
            .filter { it.preamble.isEmpty() || it.preamble == key.split(" ")[0] }
            .first()
    }
    override fun toString() = "${javaClass.simpleName}($preamble)"

    class Ok(then: Response? = null) : Response("ok", then)
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
    object NoResponse: Response("NO RESPONSE")
    data class Value(val value: String): Response("") {
        override fun toString() = "Value($value)"
    }
    data class TransmissionOKDataReceived(val response: String): Response("mac_rx") {
        val port: Int
        val data: String
        init {
            val groups = regex.matchEntire(response)?.groups
            val p = groups?.get(1)?.value
            port = if (p == null) -1 else Integer.parseInt(p)
            data = groups?.get(2)?.value ?: ""
        }
        companion object {
            private val regex = "mac_rx (?<port>\\d*) (?<data>[a-f0-9]*)".toRegex()
        }
    }
    data class DeviceInfo(val info: String) : Response("RN2483")
}

class RN2483(val port: SerialPort) {
    fun execute(command: Command): Response = with(command) {
        port.writeString(this.command)
        if (command.expectsResponse) response(port.readLine()) else Response.NoResponse
    }
}

fun String.isHex() = "[a-f0-9]*".toRegex().matches(this)
fun String.forceHex(message: String = "Must be hex: $this"): String {
    if (!isHex()) throw IllegalArgumentException(message)
    return this
}
fun String.forceSize(size: Int, message: String = "Must be $size characters long: $this"): String {
    if (size != length) throw IllegalArgumentException(message)
    return this
}
fun Int.forceRange(start: Int, end: Int): Int {
    if (this < start || this > end) throw IllegalArgumentException("$this not in allowed range [$start-$end]")
    return this
}

fun main(args: Array<String>) {
    println((Command.Mac.Set.DeviceEUI("ffffff000aaaa000").response("mac_rx 12 ffffaaffafa00000")))
}