package quoi.utils

import java.text.NumberFormat
import java.util.*
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.round

fun Number.round(decimals: Int): Number {
    require(decimals >= 0) { "Decimals must be non-negative" }
    val factor = 10.0.pow(decimals)
    return round(this.toDouble() * factor) / factor
}

inline val Number.rad get() = (toFloat() * PI / 180).toFloat()

fun Number.commas(): String = NumberFormat.getInstance(Locale.US).format(this)

private val romanMap = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
private val numberRegex = Regex("^[0-9]+$")
fun romanToInt(s: String): Int {
    return if (s.matches(numberRegex)) s.toInt()
    else {
        var result = 0
        for (i in 0 until s.length - 1) {
            val current = romanMap[s[i]] ?: 0
            val next = romanMap[s[i + 1]] ?: 0
            result += if (current < next) -current else current
        }
        result + (romanMap[s.last()] ?: 0)
    }
}

fun Number.lerp(a: Double, b: Double): Double {
    val t = this.toDouble().coerceIn(0.0, 1.0)
    return a + (b - a) * t
}

fun Number.lerpAngle(a: Double, b: Double): Double {
    var diff = (b - a) % 360.0
    if (diff < -180.0) diff += 360.0
    if (diff > 180.0) diff -= 360.0
    val t = this.toDouble().coerceIn(0.0, 1.0)
    return a + diff * t
}