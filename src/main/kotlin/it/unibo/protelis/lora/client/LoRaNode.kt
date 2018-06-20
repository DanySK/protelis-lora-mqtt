package it.unibo.protelis.lora.client

import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortDataListener
import com.fazecast.jSerialComm.SerialPortEvent
import gnu.trove.list.TByteList
import gnu.trove.list.array.TByteArrayList
import java.nio.charset.StandardCharsets
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class LoRaNode(private val serialPort: SerialPort) {

    private val readQueue: BlockingQueue<String> = LinkedBlockingQueue()

    init {
        serialPort.addDataListener(object: SerialPortDataListener {
            override fun serialEvent(event: SerialPortEvent) {
                synchronized(serialPort) {
                    serialPort.run {
                        try {
                            if (openPort()) {
                                if (bytesAvailable() > 0) {
                                    var bytes: TByteList = TByteArrayList(50)
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
                                    } while (with(bytes) {
                                                size() > 1
                                                        && bytes[size() - 2] == '\r'.toByte()
                                                        && bytes[size() - 1] == '\n'.toByte()
                                            })
                                    readQueue.put(bytes.toArray().toString(Charsets.US_ASCII))
                                }
                            } else {
                                throw IllegalStateException("Unable to open port ${serialPort.systemPortName} ${serialPort.descriptivePortName}")
                            }
                        } finally {
                            closePort()
                        }
                    }
                }
            }
            override fun getListeningEvents() = SerialPort.LISTENING_EVENT_DATA_AVAILABLE
        })
    }



    var adaptiveDataRate: Boolean
        get() = sendCommandExpectingResult("mac get adr", "on")
        set(value) {
            if (sendCommandExpectingResult("mac set adr ${if (value) "on" else "off"}")) {
                throw IllegalStateException("Unable to set adaptive data rate")
            }
        }

    @Synchronized fun sendCommand(
            command: String,
            timeout: Long = 20,
            timeoutUnit: TimeUnit = TimeUnit.SECONDS
    ): String = with(serialPort) {
        synchronized(serialPort) {
            try {
                if (openPort()) {
                    val message = "$command\r\n".toByteArray(StandardCharsets.US_ASCII)
                    val written = writeBytes(message, message.size.toLong())
                    println("written: $command ($written bytes)")
                } else {
                    throw IllegalStateException("Unable to open port ${serialPort.systemPortName} ${serialPort.descriptivePortName}")
                }
            } finally {
                closePort()
            }
            return readQueue.poll(timeout, timeoutUnit)
        }
    }

    fun sendCommandExpectingResult(command: String, result: String = "ok"): Boolean = sendCommand(command).equals(result)

    fun setNetworkSessionKey(nwkskey: String): Boolean = sendCommandExpectingResult("mac set nwkskey $nwkskey")

    fun setApplicationSessionKey(appskey: String) = sendCommandExpectingResult("mac set appskey $appskey")

    fun setDeviceAddress(devaddr: String) = sendCommandExpectingResult("mac set devaddr $devaddr")

    fun save(): Boolean = sendCommandExpectingResult("mac save")

    fun join(mode: String): String = synchronized(serialPort) {
        sendCommand("mac join $mode").let {
            if (it == "ok") readQueue.take() else it
        }
    }

    fun send(payload: ByteArray, confirmed: Boolean = false, maxPacketSize: Int = 50): Boolean =
            sendCommandExpectingResult("max tx")

}