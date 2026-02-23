package quoi.api.abobaui.constraints.impl.measurements

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.elements.Element

class Percent(private var amount: Float) : Constraint.Measurement {
    override fun calculate(element: Element, type: Int): Float {
        val size = element.parent?.getSize(type % 2 == 0) ?: return 0f
        return size * amount
    }
}