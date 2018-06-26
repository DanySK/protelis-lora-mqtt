package it.unibo.protelis.lora

import org.apache.commons.codec.binary.Hex

data class HexString(val asString: String) : CharSequence by asString {
    init {
        if (!asString.isHex()) throw IllegalArgumentException("String must be hex: $asString")
    }
    constructor(array: ByteArray) : this(Hex.encodeHexString(array))
    override fun toString() = asString
    fun toByteArray() = Hex.decodeHex(asString)
    fun forceSize(size: Int) = asString.forceSize(size)
}