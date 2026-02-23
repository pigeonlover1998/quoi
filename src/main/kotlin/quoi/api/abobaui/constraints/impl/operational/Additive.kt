package quoi.api.abobaui.constraints.impl.operational

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.elements.Element

class Additive(
    private val first: Constraint,
    private val second: Constraint
) : Constraint.Measurement {

    override fun calculate(element: Element, type: Int) = first.calculate(element, type) + second.calculate(element, type)

    override fun reliesOnChildren() = first.reliesOnChildren() || second.reliesOnChildren()

    override fun reliesOnParent() = first.reliesOnParent() || second.reliesOnParent()
}