import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortDataListener
import com.fazecast.jSerialComm.SerialPortEvent
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    val nwkskey = "94aee5a8928ebf79cdd20f53f29fef53"
    val appskey = "e3180031cbec96ffaefb60286820b0aa"
    val devaddr = "26011adc"

    val port = SerialPort.getCommPort("/dev/ttyACM12")//SerialPort.getCommPorts()[0]
    println(port.descriptivePortName)
    port.baudRate = 57600
    port.numDataBits = 8
    port.parity = SerialPort.NO_PARITY
    port.numStopBits = 1
    port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)
    port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 1000, 1000)
    println("Opening port")
    println(port.command("sys get ver"))
//    port.run {
//        command("mac set nwkskey $nwkskey").withResult()
//        command("mac set appskey $appskey").withResult()
//        command("mac set devaddr $devaddr").withResult()
//        command("mac set adr on").withResult()
//        command("mac save").withResult()
//        command("mac join abp").withResult()
//    }
////    while (true) {
//        port.command("mac tx uncnf 10 ffffffffffff")
////    }
}

fun String.withResult(result: String = "ok") {
    if(result != this) throw IllegalStateException("Expected $result, got $this")
}

fun SerialPort.command(command: String): String {
    if (openPort()) {
//        val mutex = CountDownLatch(1)
//        val listener = object: SerialPortDataListener {
//            override fun serialEvent(event: SerialPortEvent) = mutex.countDown()
//            override fun getListeningEvents() = SerialPort.LISTENING_EVENT_DATA_AVAILABLE
//        }
//        addDataListener(listener)
        val message = "$command\r\n".toByteArray(StandardCharsets.US_ASCII)
        val written = writeBytes(message, message.size.toLong())
        println("written: $command ($written bytes)")
        var sleeping = System.nanoTime()
//        if(mutex.await(20, TimeUnit.SECONDS)) {
            Thread.sleep(500)
            sleeping = System.nanoTime() - sleeping
            val buffer = ByteArray(bytesAvailable())
            val read = readBytes(buffer, buffer.size.toLong())
            val content = buffer.sliceArray(0..Math.max(0, read - 3)).toString(Charsets.US_ASCII)
            println("Read $content after ${sleeping / 1E6}ms ($read bytes)")
            return content
//        } else {
//            throw IllegalStateException("No answer from device.")
//        }
//        removeDataListener()
        closePort()
    } else {
        throw IllegalStateException("Failure opening port")
    }
}