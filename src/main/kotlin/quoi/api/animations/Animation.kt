package quoi.api.animations

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

/**
 * # Animation
 *
 * Helps assist in interpolating values, which is usually used to smoothly animate something
 *
 * @param from Starting value
 * @param to Target value
 * @param duration Duration time in nanoseconds.
 * @param style Style of the animations
 * @param lerp Custom interpolation function
 */
class Animation(
    var from: Float = 0f,
    var to: Float = 1f,
    private var duration: Float,
    private var style: Style,
    private val lerp: (from: Float, to: Float, progress: Float) -> Float = { f, t, p -> f + (t - f) * p }
) {

    constructor(duration: Float, style: Style) : this(0f, 1f, duration, style)

    private var time: Long = System.nanoTime()

    /**
     * Flag for if this animation is finished.
     *
     * NOTE: Usually this animation is discarded if this is finished.
     */
    var finished: Boolean = false

    private var onFinish: (() -> Unit)? = null

    /**
     * returns progress from 0.0 to 1.0
     */
    fun getProgress(): Float {
        val percent = ((System.nanoTime() - time) / duration)
        if (percent >= 1f) {
            finished = true
            onFinish?.invoke()
        }
        return if (finished) 1f else style.getValue(percent)
    }

    /**
     * returns interpolated float value
     */
    fun get(): Float {
        if (finished) return to
        return lerp(from, to, getProgress())
    }

    /**
     * Adds a function for this animation to run when it is finished.
     */
    fun onFinish(block: () -> Unit): Animation {
        onFinish = block
        return this
    }

    /**
     * updates the target value continuing from the current position
     */
    fun retarget(newTo: Float, duration: Float, style: Style = this.style) {
        this.from = get()
        this.to = newTo
        this.duration = duration
        this.style = style
        this.time = System.nanoTime()
        this.finished = false
    }

    /**
     * Restart this animation, allowing it to be reused, if it wasn't previously finished.
     */
    fun restart(duration: Float, style: Style) {
        this.duration = duration
        this.style = style
        this.time = System.nanoTime()
    }

    /**
     * A bunch of animations commonly used in UI
     *
     * Animations taken from [https://easings.net/](https://easings.net/)
     *
     * @see Animation
     */
    enum class Style : Strategy {
        Linear {
            override fun getValue(percent: Float): Float = percent
        },
        EaseInQuad {
            override fun getValue(percent: Float): Float = percent * percent
        },
        EaseOutQuad {
            override fun getValue(percent: Float): Float = 1 - (1 - percent) * (1 - percent)
        },
        EaseInOutQuad {
            override fun getValue(percent: Float): Float {
                return if (percent < 0.5) 2 * percent * percent
                else 1 - (-2 * percent + 2).pow(2f) / 2
            }
        },
        EaseInQuint {
            override fun getValue(percent: Float): Float = percent * percent * percent * percent * percent
        },
        EaseOutQuint {
            override fun getValue(percent: Float): Float = 1 - (1 - percent).pow(5f)
        },
        EaseInOutQuint {
            override fun getValue(percent: Float): Float {
                return if (percent < 0.5f) 16f * percent * percent * percent * percent * percent
                else 1 - (-2 * percent + 2).pow(5f) / 2f
            }
        },
        EaseInBack {
            override fun getValue(percent: Float): Float {
                val c1 = 1.70158f
                val c3 = c1 + 1
                return c3 * percent * percent * percent - c1 * percent * percent
            }
        },
        EaseOutBack {
            override fun getValue(percent: Float): Float {
                val c1 = 1.70158f
                val c3 = c1 + 1f
                return 1f + c3 * (percent - 1f).pow(3f) + c1 * (percent - 1f).pow(2f)
            }
        },

        EaseInSine {
            override fun getValue(percent: Float): Float =
                1f - cos((percent * PI.toFloat()) / 2f)
        },
    }
}

private interface Strategy {
    fun getValue(percent: Float): Float
}