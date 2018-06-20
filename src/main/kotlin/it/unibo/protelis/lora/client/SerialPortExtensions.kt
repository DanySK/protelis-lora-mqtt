package it.unibo.protelis.lora.client

import com.fazecast.jSerialComm.SerialPort
import gnu.trove.list.TByteList
import gnu.trove.list.array.TByteArrayList
import it.unibo.protelis.lora.removeLast
import java.lang.System
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

private @Synchronized inline fun <T> SerialPort.anyOperation(exec: SerialPort.() -> T): T {
    try {
        if (openPort()) {
            return exec()
        }
    } finally {
        closePort()
    }
    throw IllegalStateException("Could not open port $descriptivePortName")
}

fun SerialPort.readLine(timeout: Long = this.readTimeout.toLong(),
                        unit: TimeUnit = TimeUnit.MILLISECONDS,
                        bufferSize: Int = 10): String = anyOperation {
    var bytes: TByteList = TByteArrayList(bufferSize)
    val buffer = ByteArray(1)
    val start = System.nanoTime()
    do {
        /*
         * Read byte-per-byte until the terminator is found, waiting if neccesary
         */
        if (bytesAvailable() > 0) {
            readBytes(buffer, buffer.size.toLong())
            bytes.add(buffer)
        } else {
            Thread.sleep(1)
        }
    } while (bytes.size().let { it < 1 || bytes[it - 1] == '\n'.toByte() }
        && (System.nanoTime() - start < unit.toNanos(timeout)))
    with(bytes) {
        removeLast()
        if (this[size() - 1] == '\r'.toByte()) {
            removeLast()
        }
        toArray().toString(Charsets.US_ASCII)
    }
}

@Synchronized fun SerialPort.write(command: String): ByteArray = anyOperation {
    val message = "$command\r\n".toByteArray(StandardCharsets.US_ASCII)
    writeBytes(message, message.size.toLong())
    message
}
