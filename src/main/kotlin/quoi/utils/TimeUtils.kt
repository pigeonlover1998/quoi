package quoi.utils

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/*
 * Modified from SkyHanni code
 * Under LGPL 2.1 License
 * https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/utils/SimpleTimeMark.kt
 */
@JvmInline
value class SimpleTimeMark(val millis: Long) : Comparable<SimpleTimeMark> {
    operator fun minus(other: SimpleTimeMark) = (millis - other.millis).milliseconds
    operator fun plus(other: Duration) = SimpleTimeMark(millis + other.inWholeMilliseconds)
    operator fun minus(other: Duration) = plus(-other)

    inline val since get() = TimeUtils.now - this
    inline val until get() = -since
    inline val isInPast get() = until.isNegative()
    inline val isInFuture get() = until.isPositive()
    inline val isZero get() = millis == 0L
    inline val isMax get() = millis == Long.MAX_VALUE
    fun takeIfInitialized() = if (isZero || isMax) null else this
    fun absoluteDifference(other: SimpleTimeMark) = abs(millis - other.millis).milliseconds

    override fun compareTo(other: SimpleTimeMark): Int = millis.compareTo(other.millis)

    override fun toString(): String = when (this) {
        TimeUtils.zero -> "The Far Past"
        TimeUtils.max -> "The Far Future"
        else -> Instant.ofEpochMilli(millis).toString()
    }

    fun formattedDate(pattern: String, use24h: Boolean = true): String {
        val newPattern = if (use24h) {
            pattern.replace("h", "H").replace("a", "")
        } else {
            pattern
        }
        val instant = Instant.ofEpochMilli(millis)
        val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern(newPattern.trim())
        return localDateTime.format(formatter)
    }

    inline val toLocalDateTime get(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
    inline val toMillis get() = millis
    inline val toLocalDate get(): LocalDate = toLocalDateTime.toLocalDate()
}

object TimeUtils {
    inline val now get() = SimpleTimeMark(System.currentTimeMillis())
    inline val zero get() = SimpleTimeMark(0)
    inline val max get() = SimpleTimeMark(Long.MAX_VALUE)
    inline val Duration.fromNow get() = now + this
    inline val Long.asTimeMark get() = SimpleTimeMark(this)
    inline val Duration.millis get() = inWholeMilliseconds
}