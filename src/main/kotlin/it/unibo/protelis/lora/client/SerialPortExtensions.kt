package it.unibo.protelis.lora.client

import com.fazecast.jSerialComm.SerialPort
import gnu.trove.list.TByteList
import gnu.trove.list.array.TByteArrayList
import it.unibo.protelis.lora.removeLast
import java.util.concurrent.TimeUnit

fun SerialPort.readLine(timeout: Int = this.readTimeout,
                        unit: TimeUnit = TimeUnit.MILLISECONDS,
                        bufferSize: Int = 10): String {
    var bytes: TByteList = TByteArrayList(bufferSize)
    val buffer = ByteArray(1)
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
    } while (bytes.size().let { it < 1 || bytes[it - 1] == '\n'.toByte() })
    return with(bytes) {
        removeLast()
        if (this[size() - 1] == '\r'.toByte()) {
            removeLast()
        }
        toArray().toString(Charsets.US_ASCII)
    }
}

