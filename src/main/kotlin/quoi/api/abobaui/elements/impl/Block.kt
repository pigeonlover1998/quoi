package quoi.api.abobaui.elements.impl

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.Constraints
import quoi.api.abobaui.dsl.withScale
import quoi.api.abobaui.elements.AbobaDSL
import quoi.api.abobaui.elements.Element
import quoi.api.abobaui.elements.ElementScope
import quoi.api.colour.Colour
import quoi.utils.render.DrawContextUtils.hollowRect
import quoi.utils.ui.data.Radii
import quoi.utils.ui.rendering.NVGRenderer
import quoi.utils.ui.data.Gradient as GradientType

open class Block(
    constraints: Constraints,
    colour: Colour,
    radii: Radii?
): Element(constraints, colour) {

    protected val radii = radii ?: EMPTY_RADIUS

    protected var outline: Colour? = null
    protected var thickness: Constraint.Measurement? = null

    override fun draw() {
//        ctx.rect(x, y, width, height, colour!!.rgb)
        NVGRenderer.rect(x, y, width, height, colour!!.rgb, radii)
        if (thickness != null && outline != null) {
            val thickness = this.thickness!!.calculate(this)
//            ctx.hollowRect(x, y, width, height, thickness, outline!!.rgb)
            NVGRenderer.hollowRect(x, y, width, height, thickness, outline!!.rgb, radii)
        }
    }

    companion object {

        @JvmField
        val EMPTY_RADIUS = Radii(0f, 0f, 0f, 0f)

        @AbobaDSL
        fun ElementScope<Block>.outline(colour: Colour, thickness: Constraint.Measurement): ElementScope<Block> {
            element.outline = colour
            element.thickness = thickness
            return this
        }
    }

    class Gradient(
        constraints: Constraints,
        colourStart: Colour,
        private var colourEnd: Colour,
        private val direction: GradientType,
        radius: Radii?
    ): Block(constraints, colourStart, radius) {
        override fun draw() {
//            ctx.gradientRect(x, y, width, height, colour!!.rgb, colourEnd.rgb, direction)
            NVGRenderer.gradientRect(x, y, width, height, colour!!.rgb, colourEnd.rgb, direction, radii)
        }
    }

    open class CtxBlock( // hopefully temp
        constraints: Constraints,
        colour: Colour
    ): Block(constraints, colour, null) {
        override fun draw() {
            withScale {
                val w = width.toInt()
                val h = height.toInt()
                ctx.fill(0, 0, w, h, colour!!.rgb)

                if (thickness != null && outline != null) {
                    ctx.hollowRect(0, 0, w, h, thickness!!.calculate(this).toInt(), outline!!.rgb)
                }
            }
        }
    }
}