package quoi.api.abobaui.dsl

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.Constraints
import quoi.api.abobaui.constraints.Positions
import quoi.api.abobaui.constraints.Sizes
import quoi.api.abobaui.constraints.impl.measurements.Percent
import quoi.api.abobaui.constraints.impl.measurements.Pixel
import quoi.api.abobaui.constraints.impl.measurements.Undefined
import quoi.api.abobaui.constraints.impl.operational.Additive
import quoi.api.abobaui.constraints.impl.operational.Coercing
import quoi.api.abobaui.constraints.impl.operational.Divisive
import quoi.api.abobaui.constraints.impl.operational.Multiplicative
import quoi.api.abobaui.constraints.impl.operational.Subtractive
import quoi.api.abobaui.constraints.impl.positions.Alignment
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.constraints.impl.size.Fill

inline val Number.px: Pixel
    get() = Pixel(this.toFloat())

inline val Number.percent: Percent
    get() = Percent(this.toFloat() / 100f)

inline var Constraint.pixels: Float
    get() = (this as? Pixel)?.pixels ?: 0f
    set(value) {
        (this as? Pixel)?.pixels = value
    }

fun Constraint.coerceAtMost(maximum: Constraint): Constraint.Measurement =
    Coercing(this, maximum, true)

fun Constraint.coerceAtLeast(minimum: Constraint): Constraint.Measurement =
    Coercing(this, minimum, false)

fun Constraint.coerceIn(minimum: Constraint, maximum: Constraint): Constraint.Measurement =
    this.coerceAtLeast(minimum).coerceAtMost(maximum)

/**
 * Creates an [Alignment.Right] position from an existing [position][Constraint.Position]
 */
inline val Constraint.Position.alignRight: Alignment.Right
    get() = Alignment.Right(this)

/**
 * Creates an [Alignment.Centre] position from an existing [position][Constraint.Position]
 */
inline val Constraint.Position.alignCentre: Alignment.Centre
    get() = Alignment.Centre(this)

/**
 * Creates an [Alignment.Opposite] position from an existing [position][Constraint.Position]
 */
inline val Constraint.Position.alignOpposite: Alignment.Opposite
    get() = Alignment.Opposite(this)

inline val Constraint.Position.alignBottom: Alignment.Relative
    get() = Alignment.Relative(this, 1.0f)


/**
 * Shortened version of [Positions], for a much more appealing look.
 */
fun at(
    x: Constraint.Position = Undefined,
    y: Constraint.Position = Undefined,
) = Positions(x, y)

/**
 * Creates a [Constraints] instance, where x and y are [Undefined].
 */
fun size(
    w: Constraint.Size = Undefined,
    h: Constraint.Size = Undefined,
) = Sizes(w, h)

/**
 * Literally just [Constraints].
 */
fun constrain(
    x: Constraint.Position = Undefined,
    y: Constraint.Position = Undefined,
    w: Constraint.Size = Undefined,
    h: Constraint.Size = Undefined,
) = Constraints(x, y, w, h)

/**
 * Creates a [Constraints] instance,
 * where x and y are [0.px][Pixel] and width and height are [Copying].
 */
fun copies(gap: Constraint.Position = 0.px) = Constraints(0.px - gap, 0.px - gap, Copying + gap * 2.px, Copying + gap * 2.px)

/**
 * Creates a [Constraints] instance,
 * where x and y [amount.px][Pixel] and width and height are [Fill] - [amount][Subtractive]
 */
fun inset( // todo add %
    amount: Float,
): Constraints {
    if (amount == 0f) return copies()

    val insetPos = amount.px
    val insetSize = Fill - insetPos
    return Constraints(insetPos, insetPos, insetSize, insetSize)
}

/**
 * Creates a [Constraints] instance,
 * where the sizes are [Bounding] + [padding], with optional x and y positions.
 */
fun bounds(
    x: Constraint.Position = Undefined,
    y: Constraint.Position = Undefined,
    padding: Constraint.Size? = null
): Constraints {
    if (padding == null) return constrain(x, y, Bounding, Bounding)

    val size = Bounding + padding
    return constrain(x, y, size, size)
}

/**
 * Adds one constraint to another to create an [Additive] constraint
 */
operator fun Constraint.plus(other: Constraint) = Additive(this, other)


/**
 * Adds one constraint to another to create a [Subtractive] constraint
 */
operator fun Constraint.minus(other: Constraint) = Subtractive(this, other)


/**
 * Adds one constraint to another to create a [Multiplicative] constraint
 */
operator fun Constraint.times(other: Constraint) = Multiplicative(this, other)

/**
 * Adds one constraint to another to create a [Divisive] constraint
 */
operator fun Constraint.div(other: Constraint) = Divisive(this, other)