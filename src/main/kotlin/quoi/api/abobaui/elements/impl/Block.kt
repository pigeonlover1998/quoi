package quoi.api.abobaui.elements.impl

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.Constraints
import quoi.api.abobaui.elements.AbobaDSL
import quoi.api.abobaui.elements.Element
import quoi.api.abobaui.elements.ElementScope
import quoi.api.colour.Colour
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

    override fun drawNvg() {
        NVGRenderer.rect(x, y, width, height, colour!!.rgb, radii)
        if (thickness != null && outline != null) {
            val thickness = this.thickness!!.calculate(this)
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
        override fun drawNvg() {
            NVGRenderer.gradientRect(x, y, width, height, colour!!.rgb, colourEnd.rgb, direction, radii)
        }
    }
}