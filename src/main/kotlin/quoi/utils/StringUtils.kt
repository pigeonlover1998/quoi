package quoi.utils

import quoi.QuoiMod.mc
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.contents.PlainTextContents
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.text.uppercaseChar

object StringUtils {

    inline val textRenderer: Font get() = mc.font

    inline val fontHeight: Int get() = textRenderer.lineHeight

    private val FORMATTING_CODE_PATTERN = Regex("§[0-9a-fk-or]", RegexOption.IGNORE_CASE)

    val String?.noControlCodes: String
        get() = this?.let { FORMATTING_CODE_PATTERN.replace(it, "") } ?: ""

    val Component.formattedString: String get() = buildString {
        val rgbMap = ChatFormatting.entries
            .mapNotNull { it.color?.let { color -> color to it } }
            .toMap()

        visit<Unit>({ style, content ->
            style.color?.value?.let { rgbMap[it]?.let(::append) }
            if (style.isBold) append(ChatFormatting.BOLD)
            if (style.isItalic) append(ChatFormatting.ITALIC)
            if (style.isUnderlined) append(ChatFormatting.UNDERLINE)
            if (style.isStrikethrough) append(ChatFormatting.STRIKETHROUGH)
            if (style.isObfuscated) append(ChatFormatting.OBFUSCATED)
            append(content)
            Optional.empty()
        }, Style.EMPTY)
    }

    fun MutableComponent.trim(): MutableComponent {
        val sibs = siblings

        while (sibs.isNotEmpty()) {
            val last = sibs.last()
            val contents = last.contents as? PlainTextContents.LiteralContents ?: break

            if (last is MutableComponent && last.siblings.isNotEmpty()) {
                last.trim()
                if (last.siblings.isNotEmpty() || contents.text.isNotBlank()) break
            }

            val text = contents.text
            val trimmed = text.trimEnd()

            when {
                trimmed.isEmpty() && (last !is MutableComponent || last.siblings.isEmpty()) -> {
                    sibs.removeAt(sibs.lastIndex)
                }

                trimmed.length < text.length -> {
                    val replacement = Component.literal(trimmed)
                        .withStyle(last.style)
                    if (last is MutableComponent) {
                        replacement.siblings.addAll(last.siblings)
                    }
                    sibs[sibs.lastIndex] = replacement
                    break
                }

                else -> break
            }
        }

        return this
    }

    fun String.width(scale: Float = 1f): Float = textRenderer.width(this) * scale
    fun Component.width(scale: Float = 1f): Float = textRenderer.width(this) * scale

    fun String.startsWithOneOf(vararg options: String, ignoreCase: Boolean = false): Boolean =
        options.any { this.startsWith(it, ignoreCase) }

    fun String.containsOneOf(vararg options: String, ignoreCase: Boolean = false): Boolean =
        containsOneOf(options.toList(), ignoreCase)

    fun String.containsOneOf(options: Collection<String>, ignoreCase: Boolean = false): Boolean =
        options.any { this.contains(it, ignoreCase) }

    fun Number.toFixed(decimals: Int = 2): String =
        "%.${decimals}f".format(Locale.US, this)

    /**
     * Safer implementation of [substring], which doesn't crash.
     */
    fun String.substringSafe(from: Int, to: Int): String {
        val f = min(from, to).coerceAtLeast(0)
        val t = max(to, from)
        if (t > length) return substring(f)
        return substring(f, t)
    }

    /**
     * Safer implementation of [removeRange], which doesn't crash.
     */
    fun String.removeRangeSafe(from: Int, to: Int): String {
        val f = min(from, to)
        val t = max(to, from)
        return removeRange(f, t)
    }

    /**
     * Removes an [amount] of characters at a certain point.
     */
    fun String.dropAt(at: Int, amount: Int): String {
        return removeRangeSafe(at, at + amount)
    }

    fun String.capitaliseFirst() = this.lowercase().replaceFirstChar { it.uppercaseChar() }

    fun formatTime(
        time: Long,
        decimalPlaces: Int = 2,
        showDays: Boolean = true,
        showHours: Boolean = true,
        showMinutes: Boolean = true,
        showSeconds: Boolean = true,
        forceIfEmpty: Boolean = true
    ): String {
        if (time == 0L) return "0s"
        var remaining = time

        val daysVal = (remaining / 86_400_000).toInt()
        remaining -= daysVal * 86_400_000
        val hoursVal = (remaining / 3_600_000).toInt()
        remaining -= hoursVal * 3_600_000
        val minutesVal = (remaining / 60_000).toInt()
        remaining -= minutesVal * 60_000
        val secondsVal = remaining / 1000f

        val days = daysVal.let { if (showDays && it > 0) "${it}d " else "" }
        val hours = hoursVal.let { if (showHours && it > 0) "${it}h " else "" }
        val minutes = minutesVal.let { if (showMinutes && it > 0) "${it}m " else "" }
        val seconds = secondsVal.let { if (showSeconds && it > 0f) "${it.toFixed(decimalPlaces)}s" else "" }

        val result = "$days$hours$minutes$seconds".trim()
        if (result.isNotEmpty() || !forceIfEmpty) return result

        return when {
            secondsVal > 0f -> "${secondsVal.toFixed(decimalPlaces)}s"
            minutesVal > 0  -> "${minutesVal}m"
            hoursVal > 0    -> "${hoursVal}h"
            daysVal > 0     -> "${daysVal}d"
            else -> "0s"
        }
    }

    fun formatNumber(numStr: String): String {
        val num = numStr.replace(",", "").toDoubleOrNull() ?: return numStr
        return when {
            num >= 1_000_000_000 -> "%.2fB".format(num / 1_000_000_000)
            num >= 1_000_000 -> "%.2fM".format(num / 1_000_000)
            num >= 1_000 -> "%.2fK".format(num / 1_000)
            else -> "%.0f".format(num)
        }
    }

    fun parseNumber(str: String): Double {
        val s = str.trim().uppercase()
        return when {
            s.endsWith("B") -> s.dropLast(1).toDoubleOrNull()?.times(1_000_000_000) ?: 0.0
            s.endsWith("M") -> s.dropLast(1).toDoubleOrNull()?.times(1_000_000) ?: 0.0
            s.endsWith("K") -> s.dropLast(1).toDoubleOrNull()?.times(1_000) ?: 0.0
            else -> s.replace(",", "").toDoubleOrNull() ?: 0.0
        }
    }

    fun Number.percentColour(max: Double) = when {
        this.toDouble() >= max * 0.75 -> "§2"
        this.toDouble() >= max * 0.50 -> "§e"
        this.toDouble() >= max * 0.25 -> "§6"
        else -> "§4"
    }
}