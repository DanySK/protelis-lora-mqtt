package it.unibo.protelis.lora

import org.apache.commons.lang3.SerializationUtils
import org.protelis.vm.util.CodePath
import java.io.Serializable

interface NamedObject {
    val name: String
        get() = this::class.simpleName ?: "unknown"
}

interface Packer<out T> {
    fun pack(map: MutableMap<CodePath, Any>): T
    fun unpack(packed: Any): MutableMap<CodePath, Any>
}

object Identity : Packer<MutableMap<CodePath, Any>> {
    override fun pack(map: MutableMap<CodePath, Any>) = map
    override fun unpack(packed: Any) = packed as MutableMap<CodePath, Any>
}

interface Codec<T> : NamedObject {
    fun encode(map: T): ByteArray
    fun decode(enc: ByteArray): T
}

class JavaSerializer<T>: Codec<T> {
    override fun encode(dec: T): ByteArray = SerializationUtils.serialize(dec as Serializable)
    override fun decode(enc: ByteArray) = SerializationUtils.deserialize(enc) as T
}

interface Compressor {
    fun compress(uncompressed: ByteArray): ByteArray
    fun uncompress(compressed: ByteArray): ByteArray
}

object NoCompression : Compressor {
    override fun compress(uncompressed: ByteArray) = uncompressed
    override fun uncompress(compressed: ByteArray) = compressed
}

interface Segmenter {
    fun segment(payload: ByteArray, packetSize: Int = 50, transactionId: Byte = 0): List<ByteArray>
    fun assemble(packets: Iterable<ByteArray>): ByteArray
    fun transactionComplete(packets: Iterable<ByteArray>): Boolean
}

object ThreeBytesSegmenter : Segmenter {
    override fun segment(payload: ByteArray, packetSize: Int, transactionId: Byte): List<ByteArray> {
        if (packetSize < 4)
            throw IllegalArgumentException(
                """
                The system requires three bytes for metadata, packets must be at least 4 bytes"""
                    .trimIndent()
            )
        val sliceSize = packetSize - 3
        val packetCount: Byte = (payload.size / sliceSize + 1).toByte()
        var curpacket: Byte = 0
        return (0 until payload.size step sliceSize)
            .map { start ->
                byteArrayOf(transactionId, packetCount, curpacket++) + payload.sliceArray(start..start + sliceSize)
            }
            .toList()
    }

    override fun assemble(packets: Iterable<ByteArray>): ByteArray =
        assembleOperation(packets, AssembleStrategy { packs: List<ByteArray> ->
            packs.map { it[0].toInt() to it.sliceArray(1 until it.size) }
                .toMap().toSortedMap()
                .flatMap { it.value.toList() }
                .toByteArray()
        })

    override fun transactionComplete(packets: Iterable<ByteArray>): Boolean =
        assembleOperation(packets, AssembleStrategy (inconsistentCount = { a, b -> true }) { false })

    private fun <T> assembleOperation(packets: Iterable<ByteArray>, strategy: AssembleStrategy<T>): T {
        val transactions = packets.map { it[0] to it.sliceArray(1 until it.size) }
        if (transactions.toMap().size > 1) throw IllegalArgumentException("Multiple transactions in $transactions")
        val packs = transactions.map { it.second }
        val packetCounts = packs.map { it[0] }.toSet()
        if (packetCounts.size > 1) {
            return strategy.inconsistentSize()
        }
        val expectedPacketCount = packetCounts.first().toInt()
        if (expectedPacketCount != packs.size) {
            return strategy.inconsistentCount(expectedPacketCount, packs.size)
        }
        return strategy.compute(packs)
    }

    private class AssembleStrategy<T>(
        val inconsistentSize: () -> T = throw IllegalArgumentException("Inconsistent packet size reported"),
        val inconsistentCount: (Int, Int) -> T = { expected, actual ->
            throw IllegalArgumentException("Expected $expected packets, got ${actual}.")
        },
        val compute: (List<ByteArray>) -> T
    )
}

interface DownlinkSerializer {
    fun serialize(messages: Map<LoRaDeviceUID, ByteArray>): HexString
    fun deserialize(serialized: HexString): Map<LoRaDeviceUID, ByteArray>
}

object TwoBytesSizedHeader : DownlinkSerializer {

    override fun serialize(messages: Map<LoRaDeviceUID, ByteArray>) =
        messages.map {
            val size = Integer.toHexString(it.key.deviceAddress.length / 2 + it.value.size).let {
                when (it.length) {
                    1 -> "000$it"
                    2 -> "00$it"
                    3 -> "0$it"
                    4 -> it
                    else -> throw IllegalArgumentException("Unable to translate $it to a 4-char hex string")
                }
            }
            "$size${it.key.deviceAddress}${HexString(it.value)}"
        }.joinToString().toHex()

    override fun deserialize(serialized: HexString): Map<LoRaDeviceUID, ByteArray> {
        var remainder = serialized
        var result = emptyMap<LoRaDeviceUID, ByteArray>()
        while (remainder.length > LoRaDeviceUID.deviceAddressLength + 2) {
            val deviceEUI = serialized.substring(2 until 2 + LoRaDeviceUID.deviceAddressLength).toHex()
            val end = Integer.parseInt(serialized.substring(0..1), 16) + 2
            val payload = serialized.substring(2 + LoRaDeviceUID.deviceAddressLength until end).toHex().toByteArray()
            result += LoRaDeviceUID(deviceEUI) to payload
            remainder = remainder.substring(end).toHex()
        }
        return result
    }

}