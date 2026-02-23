package quoi.api.abobaui.constraints.impl.operational

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.elements.Element
import kotlin.math.max
import kotlin.math.min

class Coercing(
    private val constraint: Constraint,
    private val limit: Constraint,
    private val isAtMost: Boolean
) : Constraint.Measurement {

    override fun calculate(element: Element, type: Int): Float {
        val value = constraint.calculate(element, type)
        val limitValue = limit.calculate(element, type)

        return if (isAtMost) min(value, limitValue) else max(value, limitValue)
    }

    override fun reliesOnChildren(): Boolean = constraint.reliesOnChildren() || limit.reliesOnChildren()
    override fun reliesOnParent(): Boolean = constraint.reliesOnParent() || limit.reliesOnParent()
}