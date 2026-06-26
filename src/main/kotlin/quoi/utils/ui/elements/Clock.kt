package quoi.utils.ui.elements

import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.dsl.constrain
import quoi.api.abobaui.dsl.copies
import quoi.api.abobaui.dsl.plus
import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.dsl.radius
import quoi.api.abobaui.dsl.rotation
import quoi.api.abobaui.dsl.seconds
import quoi.api.abobaui.dsl.size
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.Layout.Companion.divider
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.animations.Animation
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.watch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val d12 = DateTimeFormatter.ofPattern("hhmmss")
private val d24 = DateTimeFormatter.ofPattern("HHmmss")

private var format = false
private var speed = 0.4

// https://www.humanssince1982.com/products/clockclock-24-white
fun ElementScope<*>.clock(twelve: Boolean, spd: Double) = block(
    size(Bounding + 20.px, Bounding + 20.px),
    theme.surfaceContainer,
    radius = 24.radius()
) {
    outline(theme.outlineVariant, thickness = 1.px)

    val digits = mutableListOf<Digit>()
    format = twelve
    speed = spd

    row(gap = 4.px) {
        time().forEachIndexed { i, c ->
            digits.add(digit(c))
            if (i == 1 || i == 3) divider(20.px)
        }
    }

    watch(::time) { time ->
        digits.forEachIndexed { i, digit ->
            digit.update(time[i])
        }
    }
}

private fun ElementScope<*>.cell(char: Char): Cell {
    val (r1, r2) = rotations[char] ?: rotations[' ']!!
    val h1 = Hand(r1)
    val h2 = Hand(r2)

    block(
        size(24.px, 24.px),
        colour = theme.surfaceContainerHigh,
        radius = 12.radius(),
    ) {
        outline(theme.outlineVariant, thickness = 1.px)
        group(copies()) {
            block(constrain(12.px, 11.px, 11.5.px, 1.5.px), theme.primary)
        }.rotation { h1.rotation }

        group(copies()) {
            block(constrain(12.px, 11.px, 11.5.px, 1.5.px), theme.primary)
        }.rotation { h2.rotation }

//            block(
//                constrain(11.px, 11.px, 2.px, 1.5.px),
//                Colour.BLUE,
//            )
    }
    return Cell(h1, h2)
}

private fun ElementScope<*>.digit(char: Char): Digit = Digit(
    buildList {
        val digit = digits[char] ?: digits['0']!!
        column(gap = 4.px) {
            repeat(6) { r ->
                row(gap = 4.px) {
                    repeat(4) { c ->
                        add(cell(digit[r * 4 + c]))
                    }
                }
            }
        }
    }
)

private class Hand(rot: Float) {
    val anim = Animation(
        from = rot,
        to = rot,
        duration = 0f,
        style = Animation.Style.Linear,
    )
    val rotation get() = anim.get()

    fun setTarget(new: Float) {
        anim.retarget(new, speed.seconds)
    }
}

private class Cell(val h1: Hand, val h2: Hand) {
    fun setTarget(r1: Float, r2: Float) {
        h1.setTarget(r1)
        h2.setTarget(r2)
    }
}

private class Digit(val cells: List<Cell>) {
    fun update(value: Char) {
        val digit = digits[value] ?: digits['0']!!
        cells.forEachIndexed { i, cell ->
            val symbol = digit[i]
            val (r1, r2) = rotations[symbol] ?: rotations[' ']!!
            cell.setTarget(r1, r2)
        }
    }
}

private fun time() =
    LocalTime.now().format(if (format) d12 else d24)

private val rotations = mapOf(
    ' ' to (135f to 135f),
    '⅃' to (180f to 270f),
    'L' to (0f to 270f),
    '⅂' to (90f to 180f),
    'Г' to (0f to 90f),
    '-' to (0f to 180f),
    '|' to (90f to 270f)
)

private val digits = mapOf(
    '0' to listOf(
        'Г', '-', '-', '⅂',
        '|', 'Г', '⅂', '|',
        '|', '|', '|', '|',
        '|', '|', '|', '|',
        '|', 'L', '⅃', '|',
        'L', '-', '-', '⅃'
    ),
    '1' to listOf(
        'Г', '-', '⅂', ' ',
        'L', '⅂', '|', ' ',
        ' ', '|', '|', ' ',
        ' ', '|', '|', ' ',
        'Г', '⅃', 'L', '⅂',
        'L', '-', '-', '⅃'
    ),
    '2' to listOf(
        'Г', '-', '-', '⅂',
        'L', '-', '⅂', '|',
        'Г', '-', '⅃', '|',
        '|', 'Г', '-', '⅃',
        '|', 'L', '-', '⅂',
        'L', '-', '-', '⅃'
    ),
    '3' to listOf(
        'Г', '-', '-', '⅂',
        'L', '-', '⅂', '|',
        ' ', 'Г', '⅃', '|',
        ' ', 'L', '⅂', '|',
        'Г', '-', '⅃', '|',
        'L', '-', '-', '⅃'
    ),
    '4' to listOf(
        'Г', '⅂', 'Г', '⅂',
        '|', '|', '|', '|',
        '|', 'L', '⅃', '|',
        'L', '-', '⅂', '|',
        ' ', ' ', '|', '|',
        ' ', ' ', 'L', '⅃'
    ),
    '5' to listOf(
        'Г', '-', '-', '⅂',
        '|', 'Г', '-', '⅃',
        '|', 'L', '-', '⅂',
        'L', '-', '⅂', '|',
        'Г', '-', '⅃', '|',
        'L', '-', '-', '⅃'
    ),
    '6' to listOf(
        'Г', '-', '-', '⅂',
        '|', 'Г', '-', '⅃',
        '|', 'L', '-', '⅂',
        '|', 'Г', '⅂', '|',
        '|', 'L', '⅃', '|',
        'L', '-', '-', '⅃'
    ),
    '7' to listOf(
        'Г', '-', '-', '⅂',
        'L', '-', '⅂', '|',
        ' ', ' ', '|', '|',
        ' ', ' ', '|', '|',
        ' ', ' ', '|', '|',
        ' ', ' ', 'L', '⅃'
    ),
    '8' to listOf(
        'Г', '-', '-', '⅂',
        '|', 'Г', '⅂', '|',
        '|', 'L', '⅃', '|',
        '|', 'Г', '⅂', '|',
        '|', 'L', '⅃', '|',
        'L', '-', '-', '⅃'
    ),
    '9' to listOf(
        'Г', '-', '-', '⅂',
        '|', 'Г', '⅂', '|',
        '|', 'L', '⅃', '|',
        'L', '-', '⅂', '|',
        'Г', '-', '⅃', '|',
        'L', '-', '-', '⅃'
    )
)