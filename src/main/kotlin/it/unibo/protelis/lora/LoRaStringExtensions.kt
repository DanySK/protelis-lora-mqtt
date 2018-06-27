package it.unibo.protelis.lora

fun String.isHex() = "[A-Fa-f0-9]*".toRegex().matches(this)

fun String.toHex(message: String = "Must be hex: $this"): HexString = HexString(this)

fun String.forceSize(size: Int, message: String = "Must be $size characters long: $this"): String = this.also {
    if (size != length) throw IllegalArgumentException(message)
}
