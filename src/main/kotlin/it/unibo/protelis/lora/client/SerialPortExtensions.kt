package it.unibo.protelis.lora.client

import com.fazecast.jSerialComm.SerialPort
import gnu.trove.list.TByteList
import gnu.trove.list.array.TByteArrayList
import it.unibo.protelis.lora.removeLast
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

fun SerialPort.readLine(timeout: Long = 1,
                        unit: TimeUnit = TimeUnit.SECONDS,
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
    } while (bytes.size().let { it < 1 || bytes[it - 1] != '\n'.toByte() }
        && (System.nanoTime() - start < unit.toNanos(timeout)))
    with(bytes) {
        if (this.size() > 0) {
            removeLast()
            if (this.size() > 0 && this[size() - 1] == '\r'.toByte()) {
                removeLast()
            }
        }
        return toArray().toString(Charsets.US_ASCII).also { println("Read: $it") }
    }
}

fun SerialPort.purge(timeout: Long = 1, unit: TimeUnit = TimeUnit.SECONDS): String? =
    generateSequence { readLine(timeout, unit) }
        .takeWhile { it.isNotEmpty() }
        .joinToString()

@Synchronized fun SerialPort.write(command: String): ByteArray = anyOperation {
    val message = "$command\r\n".toByteArray(StandardCharsets.US_ASCII)
    println("writing: $command")
    writeBytes(message, message.size.toLong())
    message
}
