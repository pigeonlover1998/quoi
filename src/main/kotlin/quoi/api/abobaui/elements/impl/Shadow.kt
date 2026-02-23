package quoi.api.abobaui.elements.impl

import quoi.api.abobaui.constraints.Constraints
import quoi.api.abobaui.elements.Element
import quoi.api.colour.Colour
import quoi.utils.ui.rendering.NVGRenderer
import quoi.utils.ui.data.Radii

class Shadow(
    constraints: Constraints,
    colour: Colour,
    private val blur: Float,
    private val spread: Float,
    private val offsetX: Float,
    private val offsetY: Float,
    radii: Radii?,
) : Element(constraints, colour) {

    private val radii = radii ?: Block.EMPTY_RADIUS

    override fun draw() {
        NVGRenderer.dropShadow(x + offsetX, y + offsetY, width, height, blur, spread, radii, colour!!.rgb)
    }
}