package it.unibo.protelis.lora.client

sealed class Command (val command: String)


sealed class System(subcommand: String) : Command("sys $subcommand")
class Sleep(length: Int) : System("sleep $length") {
    init {
        if (!(length in 100..429967296)) {
            throw IllegalArgumentException("invalid length")
        }
    }
}
class
sealed class Response

//class Ok : Response()