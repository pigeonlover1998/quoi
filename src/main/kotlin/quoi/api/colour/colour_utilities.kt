@file:Suppress("unused", "nothing_to_inline")

package quoi.api.colour

import java.awt.Color as JavaColour
import kotlin.math.roundToInt

inline val Int.red
    get() = this shr 16 and 0xFF

inline val Int.green
    get() = this shr 8 and 0xFF

inline val Int.blue
    get() = this and 0xFF

inline val Int.alpha
    get() = (this shr 24) and 0xFF

inline val Colour.red
    get() = rgb.red

inline val Colour.green
    get() = rgb.green

inline val Colour.blue
    get() = rgb.blue

inline val Colour.alpha
    get() = rgb.alpha

inline val Colour.redFloat get() = red / 255f
inline val Colour.greenFloat get() = green / 255f
inline val Colour.blueFloat get() = blue / 255f
inline val Colour.alphaFloat get() = alpha / 255f

inline fun Colour.RGB.copy(
    red: Int = this.red,
    green: Int = this.green,
    blue: Int = this.blue,
    alpha: Float = this.alphaFloat
) = Colour.RGB(red, green, blue, alpha)

inline fun Colour.HSB.copy(
    hue: Float = this.hue,
    saturation: Float = this.saturation,
    brightness: Float = this.brightness,
    alpha: Float = this.alpha
) = Colour.HSB(hue, saturation, brightness, alpha)

/**
 * Compacts 4 integers representing red, green, blue and alpha into a single integer
 */
fun getRGBA(red: Int, green: Int, blue: Int, alpha: Int): Int {
    return ((alpha shl 24) and 0xFF000000.toInt()) or ((red shl 16) and 0x00FF0000) or ((green shl 8) and 0x0000FF00) or (blue and 0x000000FF)
}


/**
 * Compacts 3 integers representing red, green, blue and an alpha value into a single integer
 */
fun getRGBA(red: Int, green: Int, blue: Int, alpha: Float): Int {
    return (((alpha * 255).roundToInt() shl 24) and 0xFF000000.toInt()) or ((red shl 16) and 0x00FF0000) or ((green shl 8) and 0x0000FF00) or (blue and 0x000000FF)
}

/**
 * Gets an RGBA value from a hexadecimal colour string (#RRGGBB or #RRGGBBAA).
 *
 * @throws IllegalArgumentException If hex value is invalid.
 */
fun hexToRGBA(hex: String): Int {
    return when (hex.length) {
        7 -> (255 shl 24) or hex.substring(1, 7).toInt(16)
        9 -> (hex.substring(7, 9).toInt(16) shl 24) or hex.substring(1, 7).toInt(16) // needs 2 substrings because it is unable to correctly parse with 1
        else -> throw IllegalArgumentException("Invalid hex colour format: $hex. Use #RRGGBB or #RRGGBBAA.")
    }
}

/**
 * Gets a string representing a hexadecimal colour value. (#RRGGBB or #RRGGBBAA)
 */
fun Colour.toHexString(returnAlpha: Boolean = false): String {
    return if (returnAlpha) {
        String.format("#%02X%02X%02X%02X", red, green, blue, alpha)
    } else {
        String.format("#%02X%02X%02X", red, green, blue)
    }
}

/**
 * Copies a colour with the new alpha value provided.
 */
inline fun Colour.withAlpha(alpha: Float): Colour = Colour.RGB(red, green, blue, alpha)

/**
 * Copies a colour with the new alpha value provided.
 */
inline fun Colour.withAlpha(alpha: Int): Colour = Colour.RGB(red, green, blue, alpha / 255f)

/**
 * Copies a colour, multiplying its alpha value by a certain factor
 */
inline fun Colour.multiplyAlpha(factor: Float): Colour = withAlpha((alpha * factor).roundToInt())

/**
 * Multiples an integer representing a hexadecimal colour.
 */
inline fun Int.multiply(
    r: Float = 1f,
    g: Float = 1f,
    b: Float = 1f,
    a: Float = 1f
) = getRGBA(
    (red * r).roundToInt().coerceIn(0, 255),
    (green * g).roundToInt().coerceIn(0, 255),
    (blue * b).roundToInt().coerceIn(0, 255),
    (alpha * a).roundToInt().coerceIn(0, 255)
)

/**
 * Multiples red, blue and green from a hexadecimal colour by a factor.
 */
inline fun Int.multiply(factor: Float = 1f) = multiply(factor, factor, factor, 1f)

/**
 * Converts any [Colour] into a [Colour.HSB]
 */
fun Colour.toHSB(): Colour.HSB {
    return Colour.HSB(
        JavaColour.RGBtoHSB(
            red,
            green,
            blue,
            FloatArray(size = 3)
        ),
        alpha / 255f
    )
}

/**
 * Implementation of [Colour], where it gets supplied the rgba value from [block].
 *
 * This isn't recommended to use with expensive calculations because it runs every frame.
 */
inline fun colour(crossinline block: () -> Int): Colour = object : Colour {
    override val rgb: Int
        get() = block()
}

inline fun hsb(crossinline block: () -> Colour.HSB): Colour.HSB = object : Colour.HSB(0f, 0f, 0f) {
    override var hue: Float
        get() = block().hue
        set(value) { super.hue = value }
    override var saturation: Float
        get() = block().saturation
        set(value) { super.saturation = value }
    override var brightness: Float
        get() = block().brightness
        set(value) { super.brightness = value }
    override var alpha: Float
        get() = block().alpha
        set(value) { super.alpha = value }

    override var rgb: Int
        get() = block().rgb
        set(_) {}
}