import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortDataListener
import com.fazecast.jSerialComm.SerialPortEvent
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    val nwkskey = "94aee5a8928ebf79cdd20f53f29fef53"
    val appskey = "e3180031cbec96ffaefb60286820b0aa"
    val devaddr = "26011adc"
    val needsConfig = false;

    val port = SerialPort.getCommPort("/dev/ttyACM1")//SerialPort.getCommPorts()[0]
    println(port.descriptivePortName)
    port.baudRate = 57600
    port.numDataBits = 8
    port.parity = SerialPort.NO_PARITY
    port.numStopBits = 1
    port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)
    port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 1000, 1000)
    println("Opening port")
    println(port.writeString("sys get ver"))
    port.takeIf { needsConfig }?.run {
        writeString("mac set nwkskey $nwkskey").withResult()
        writeString("mac set appskey $appskey").withResult()
        writeString("mac set devaddr $devaddr").withResult()
        writeString("mac set adr on").withResult()
        writeString("mac save").withResult()
    }
    port.writeString("mac join abp")
    with(port) {
//        writeString("mac set adr off")
//        writeString("mac set dr 5")
//        writeString("mac save").withResult()
        (1..3).forEach { writeString("mac set ch status $it off") }
    }
    generateSequence(0) { (it + 1) % 255  }.forEach { transaction ->
        (0..3).forEach {frame ->
            val tr = transaction.toHexByte()
            val fr = frame.toHexByte()
            println("tr hex: $tr")
            println("fr hex: $fr")
            port.writeString("mac tx uncnf 10 ff$tr${fr}")
            Thread.sleep(30000)

        }
    }
}

fun Int.toHexByte(): String = Integer.toHexString(this).let { when (it.length) {
    1 -> "0$it"
    2 -> it
    else -> throw IllegalArgumentException("$this is not a valid byte ($it)")
} }

fun String.withResult(result: String = "ok") {
    if(result != this) throw IllegalStateException("Expected $result, got $this")
}

@Synchronized fun SerialPort.writeString(command: String): String {
    try {
        if (openPort()) {
            val mutex = CountDownLatch(1)
            val listener = object: SerialPortDataListener {
                override fun serialEvent(event: SerialPortEvent) = mutex.countDown()
                override fun getListeningEvents() = SerialPort.LISTENING_EVENT_DATA_AVAILABLE
            }
            addDataListener(listener)
            val message = "$command\r\n".toByteArray(StandardCharsets.US_ASCII)
            val written = writeBytes(message, message.size.toLong())
            println("written: $command ($written bytes)")
            var sleeping = System.nanoTime()
            if(mutex.await(200, TimeUnit.SECONDS)) {
                do {
                    var bytes = bytesAvailable()
                    Thread.sleep(500)
                } while (bytes != bytesAvailable())
                sleeping = System.nanoTime() - sleeping
                val buffer = ByteArray(bytesAvailable())
                val read = readBytes(buffer, buffer.size.toLong())
                val content = buffer.sliceArray(0..Math.max(0, read - 3)).toString(Charsets.US_ASCII)
                println("Read $content after ${sleeping / 1E6}ms ($read bytes)")
                return content
            } else {
                throw IllegalStateException("No answer from device.")
            }
        } else {
            throw IllegalStateException("Failure opening port")
        }
    } finally {
        removeDataListener()
        closePort()
    }
}