package it.unibo.protelis.lora

fun String.isHex() = "[a-f0-9]*".toRegex().matches(this)
fun String.forceHex(message: String = "Must be hex: $this"): String {
    if (!isHex()) throw IllegalArgumentException(message)
    return this
}
fun String.forceSize(size: Int, message: String = "Must be $size characters long: $this"): String {
    if (size != length) throw IllegalArgumentException(message)
    return this
}
