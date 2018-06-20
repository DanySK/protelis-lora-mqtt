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
    fun segment(payload: ByteArray, packetSize: Int = 51, transactionId: Byte = 0): List<ByteArray>
    fun assemble(packets: Iterable<ByteArray>): ByteArray
}

object ThreeBytesSegmenter : Segmenter {
    override fun segment(payload: ByteArray, packetSize: Int, transactionId: Byte): List<ByteArray> {
        if (packetSize < 4)
            throw IllegalArgumentException("""
                The system requires three bytes for metadata, packets must be at least 4 bytes"""
            .trimIndent())
        val sliceSize = packetSize - 3
        val packetCount: Byte = (payload.size / sliceSize + 1).toByte()
        var curpacket: Byte = 0
        return (0 until payload.size step sliceSize)
            .map { start ->
                byteArrayOf(transactionId, packetCount, curpacket++) + payload.sliceArray(start..start + sliceSize)
            }
            .toList()
    }

    override fun assemble(packets: Iterable<ByteArray>): ByteArray {
        val transactions = packets.map { it[0] to it.sliceArray(1 until it.size) }
        if (transactions.toMap().size > 1) throw IllegalArgumentException("Multiple transactions in $transactions")
        val packs = transactions.map { it.second }
        val packetCounts = packs.map { it[0] }.toSet()
        if (packetCounts.size > 1) {
            throw IllegalArgumentException("Inconsistent packet size reported")
        }
        val expectedPacketCount = packetCounts.first().toInt()
        if (expectedPacketCount != packs.size) {
            throw IllegalArgumentException("Expected $expectedPacketCount packets, got ${packs.size}.")
        }
        val payloads = packs.map { it[0].toInt() to it.sliceArray(1 until it.size) }.toMap().toSortedMap()
        return payloads.flatMap { it.value.toList() }.toByteArray()
    }

}