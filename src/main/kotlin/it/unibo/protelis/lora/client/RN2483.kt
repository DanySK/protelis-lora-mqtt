package it.unibo.protelis.lora.client

import com.fazecast.jSerialComm.SerialPort
import it.unibo.protelis.lora.HexString
import it.unibo.protelis.lora.force16bit
import it.unibo.protelis.lora.force3bit
import it.unibo.protelis.lora.force4bit
import it.unibo.protelis.lora.force8bit
import it.unibo.protelis.lora.forceRange
import it.unibo.protelis.lora.forceValidFrequency
import it.unibo.protelis.lora.isHex
import it.unibo.protelis.lora.toHex
import it.unibo.protelis.lora.toOnOff
import java.util.concurrent.TimeUnit
import kotlin.reflect.full.isSubclassOf

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
                val data: HexString,
                val confirmed: Boolean = false,
                val port: Int = (Math.random() * 223).toInt() + 1)
            : Mac("tx ${if (confirmed) "" else "un"}cnf ${port.forceRange(1, 233)} ${data}") {
            override fun response(response: String): Response =
                super.response(response).let {
                    when (it) {
                        is Response.Ok -> Response.Ok(fetchAnotherResponse())
                        is Response.DataReceived -> Response.DataReceived(it.data, fetchAnotherResponse())
                        is Response.TransmissionOKNoReply -> it
                        else -> it
                    }
                }

            private fun fetchAnotherResponse() = response(device.port.readLine(5, TimeUnit.MINUTES))
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
            data class DeviceAddress(val address: HexString): Set("devaddr $address") {
                init { address.forceSize(8) }
            }
            data class DeviceEUI(val devEUI: HexString)
                : Set("deveui ${devEUI.forceSize(16)}")
            data class ApplicationEUI(val appEUI: HexString)
                : Set("appeui ${appEUI.forceSize(16)}")
            data class NetworkSessionKey(val networkSessionKey: HexString)
                : Set("nwkskey ${networkSessionKey.forceSize(32)}")
            data class ApplicationSessionKey(val applicationSessionKey: HexString)
                : Set("appskey ${applicationSessionKey.forceSize(32)}") {
            }
            data class ApplicationKey(val applicationKey: HexString)
                : Set("appkey ${applicationKey.forceSize(32)}")
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

sealed class Response(val preamble: String, val then: Response? = null) {
    companion object {
        val candidates = Response::class.nestedClasses.asSequence()
            .filter { it.isFinal && it.isSubclassOf(Response::class) }
            .sortedBy { if (it.objectInstance == null) 1 else 0 }
        fun get(key: String) = candidates
            .map{ it.objectInstance ?: when {
                key.startsWith(DataReceived.preamble) -> DataReceived(key)
                key.startsWith(Ok.preamble) -> Ok.empty
                key.startsWith(DeviceInfo.preamble) -> DeviceInfo(key)
                else -> Value(key)
            }}
            .map { it as Response }
            .filter { it.preamble.isEmpty() || it.preamble == key.split(" ")[0] }
            .first()
    }
    override fun toString() = "${javaClass.simpleName}($preamble)"

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
    data class DeviceInfo(val info: String) : Response(preamble) {
        companion object {
            val preamble = "RN2483"
        }
    }
    class Ok(then: Response? = null) : Response(preamble, then) {
        companion object {
            val preamble = "ok"
            val empty: Ok = Ok()
        }
    }
    class DataReceived(val response: String, then: Response? = null): Response(preamble, then) {
        val port: Int
        val data: String
        init {
            val groups = regex.matchEntire(response)?.groups
            val p = groups?.get(1)?.value
            port = if (p == null) -1 else Integer.parseInt(p)
            data = groups?.get(2)?.value ?: ""
        }
        companion object {
            val preamble = "mac_rx"
            private val regex = "$preamble (?<port>\\d*) (?<data>[a-f0-9]*)".toRegex()
        }
    }
}

class RN2483(val port: SerialPort) {
    fun execute(command: Command, timeout: Long = 1, unit: TimeUnit = TimeUnit.SECONDS): Response = with(command) {
        port.write(this.command)
        if (command.expectsResponse) response(port.readLine(timeout, unit)) else Response.NoResponse
    }
}

fun main(args: Array<String>) {
    println((Command.Mac.Set.DeviceEUI("ffffff000aaaa000".toHex()).response("mac_rx 12 ffffaaffafa00000")))
}