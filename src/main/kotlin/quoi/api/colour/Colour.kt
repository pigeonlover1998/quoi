package quoi.api.colour

import quoi.api.animations.Animation
import quoi.module.impl.render.ClickGui.rainbowSpeed
import kotlin.math.roundToInt
import java.awt.Color as JavaColour

interface Colour {

    val rgb: Int

    /**
     * # Colour.RGB
     *
     * Low cost implementation of [Colour], due to Kotlin's inline classes
     *
     * This is the most common [color][Colour], because it mainly represents red, blue and green.
     */
    @JvmInline
    value class RGB(override val rgb: Int) : Colour {
        constructor(
            red: Int,
            green: Int,
            blue: Int,
            alpha: Float = 1f
        ) : this(getRGBA(red, green, blue, (alpha * 255).roundToInt()))

        constructor(
            h: Float,
            s: Float,
            b: Float
        ) : this(JavaColour.HSBtoRGB(h, s, b))
    }

    /**
     * # Colour.HSB
     *
     * This [Colour] implementation represents the color in HSBA format.
     *
     * It only updates the [rgba][Colour.rgb] value if any of the hue, saturation or brightness values have been changed.
     */
    open class HSB(hue: Float, saturation: Float, brightness: Float, alpha: Float = 1f) : Colour {

        constructor(hsb: FloatArray, alpha: Float = 1f) : this(hsb[0], hsb[1], hsb[2], alpha)

        constructor(other: HSB) : this(other.hue, other.saturation, other.brightness, other.alpha)

        open var hue = hue
            set(value) {
                field = value
                needsUpdate = true
            }

        open var saturation = saturation
            set(value) {
                field = value
                needsUpdate = true
            }

        open var brightness = brightness
            set(value) {
                field = value
                needsUpdate = true
            }

        open var alpha = alpha
            set(value) {
                field = value
                needsUpdate = true
            }

        @Transient
        private var needsUpdate: Boolean = true

        var red: Int
            get() = rgb.red
            set(value) {
                rgb = getRGBA(value, rgb.green, rgb.blue, rgb.alpha)
            }

        var green: Int
            get() = rgb.green
            set(value) {
                rgb = getRGBA(rgb.red, value, rgb.blue, rgb.alpha)
            }

        var blue: Int
            get() = rgb.blue
            set(value) {
                rgb = getRGBA(rgb.red, rgb.green, value, rgb.alpha)
            }

        override var rgb: Int = 0
            get() {
                if (needsUpdate) {
                    field =
                        (JavaColour.HSBtoRGB(hue, saturation, brightness) and 0X00FFFFFF) or ((alpha * 255).toInt() shl 24)
                    needsUpdate = false
                }
                return field
            }
            set(value) {
                if (field != value) {
                    field = value
                    val hsb = FloatArray(3)
                    JavaColour.RGBtoHSB(value.red, value.blue, value.green, hsb)
                    hue = hsb[0]
                    saturation = hsb[1]
                    brightness = hsb[2]
                    alpha = value.alpha / 255f
                }
            }

        override fun equals(other: Any?): Boolean {
            return other is HSB &&
                    other.hue == hue && other.saturation == saturation &&
                    other.brightness == brightness &&
                    other.alpha == alpha && other.rgb == rgb
        }

        override fun hashCode(): Int {
            var result = hue.hashCode()
            result = 31 * result + saturation.hashCode()
            result = 31 * result + brightness.hashCode()
            result = 31 * result + alpha.hashCode()
            return result
        }
    }

    /**
     * # Color.Animated
     *
     * This [Colour] implementation allows you to animate between 2 different colors, utilizing [Animations][Animation].
     *
     * @see Animation
     */
    class Animated(from: Colour, to: Colour) : Colour {

        constructor(from: Colour, to: Colour, swapIf: Boolean) : this(from, to) {
            if (swapIf) {
                swap()
            }
        }

        /**
         * Current animation for this [Colour.Animated].
         *
         * If this is null, that means it isn't animating.
         */
        var animation: Animation? = null

        /**
         * The color to animate from.
         *
         * When an animation is finished, it will swap with [color2].
         */
        private var color1: Colour = from

        /**
         * The color to animate to.
         *
         * When an animation is finished, it will swap with [color1].
         */
        private var color2: Colour = to

        private var from: Int = color1.rgb

        override val rgb: Int
            get() {
                if (animation != null) {
                    val progress = animation!!.get()
                    val to = color2.rgb
                    val current = getRGBA(
                        (from.red + (to.red - from.red) * progress).toInt(),
                        (from.green + (to.green - from.green) * progress).toInt(),
                        (from.blue + (to.blue - from.blue) * progress).toInt(),
                        (from.alpha + (to.alpha - from.alpha) * progress).toInt(),
                    )
                    if (animation!!.finished) {
                        animation = null
                        swap()
                    }
                    return current
                }
                return color1.rgb
            }


        fun animate(duration: Float, style: Animation.Style): Animation? {
            if (duration == 0f) {
                swap()
                from = color1.rgb // here so it updates if you swap a color and want to animate it later
                return null
            }

            if (animation != null) {
                swap()
                animation = Animation(duration * animation!!.get(), style)
                from = rgb
                return animation
            }

            animation = Animation(duration, style)
            from = color1.rgb
            return animation
        }

        fun swap() {
            val temp = color2
            color2 = color1
            color1 = temp
        }
    }

    companion object {
        @JvmField
        val TRANSPARENT: RGB = RGB(0, 0, 0, 0f)

        @JvmField
        val WHITE: RGB = RGB(255, 255, 255, 1f)

        @JvmField
        val BLACK: RGB = RGB(0, 0, 0, 1f)

        @JvmField
        val RED = RGB(255, 0, 0)

        @JvmField
        val BLUE = RGB(0, 0, 255)

        @JvmField
        val GREEN = RGB(0, 255, 0)

        @JvmField
        val YELLOW: RGB = RGB(255, 255, 0)

        @JvmField
        val CYAN: RGB = RGB(0, 255, 255)

        @JvmField
        val MAGENTA: RGB = RGB(255, 0, 255)

        @JvmField
        val ORANGE: RGB = RGB(255, 165, 0)

        @JvmField
        val PURPLE: RGB = RGB(128, 0, 128)

        @JvmField
        val GREY: RGB = RGB(128, 128, 128)

        @JvmField
        val PINK: RGB = RGB(242, 127, 165)

        @JvmField
        val LIME: RGB = RGB(50, 205, 50)

        @JvmField
        val BROWN: RGB = RGB(165, 42, 42)

        @JvmField
        val RAINBOW: Colour = colour {
            val hue = ((System.nanoTime() * 1e-9 * rainbowSpeed) % 1.0).toFloat()
            (JavaColour.HSBtoRGB(hue, 1f, 1f) and 0x00FFFFFF) or (0xFF shl 24)
        }

        @JvmField val MINECRAFT_DARK_BLUE = RGB(0, 0, 170)
        @JvmField val MINECRAFT_DARK_GREEN = RGB(0, 170, 0)
        @JvmField val MINECRAFT_DARK_AQUA = RGB(0, 170, 170)
        @JvmField val MINECRAFT_DARK_RED = RGB(170, 0, 0)
        @JvmField val MINECRAFT_DARK_PURPLE = RGB(170, 0, 170)
        @JvmField val MINECRAFT_GOLD = RGB(255, 170, 0)
        @JvmField val MINECRAFT_GRAY = RGB(170, 170, 170)
        @JvmField val MINECRAFT_DARK_GRAY = RGB(85, 85, 85)
        @JvmField val MINECRAFT_BLUE = RGB(85, 85, 255)
        @JvmField val MINECRAFT_GREEN = RGB(85, 255, 85)
        @JvmField val MINECRAFT_AQUA = RGB(85, 255, 255)
        @JvmField val MINECRAFT_RED = RGB(255, 85, 85)
        @JvmField val MINECRAFT_LIGHT_PURPLE = RGB(255, 85, 255)
        @JvmField val MINECRAFT_YELLOW = RGB(255, 255, 85)
    }
}