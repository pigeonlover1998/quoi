package quoi.api.abobaui.constraints.impl.measurements

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.elements.Element

class Pixel(var pixels: Float) : Constraint.Measurement {

    override fun calculate(element: Element, type: Int): Float {
        return pixels
    }
}