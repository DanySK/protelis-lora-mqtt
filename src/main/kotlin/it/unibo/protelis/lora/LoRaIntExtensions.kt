package it.unibo.protelis.lora

fun Int.forceRange(start: Int, end: Int): Int {
    if (this < start || this > end) throw IllegalArgumentException("$this not in allowed range [$start-$end]")
    return this
}

fun Int.forceValidFrequency(): Int = this.let {
    if (it < 500000000) it.forceRange(433050000, 434790000)
    else it.forceRange(863000000, 870000000)
}

fun Int.force16bit(): Int = this.forceRange(0, 65535)
fun Int.force8bit(): Int = this.forceRange(0, 255)
fun Int.force4bit(): Int = this.forceRange(0, 15)
fun Int.force3bit(): Int = this.forceRange(0, 7)
