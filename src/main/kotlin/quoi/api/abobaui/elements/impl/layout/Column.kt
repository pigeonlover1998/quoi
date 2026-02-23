package quoi.api.abobaui.elements.impl.layout

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.Constraints
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.size
import quoi.api.abobaui.elements.AbobaDSL
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.Layout

class Column(
    constraints: Constraints,
    gap: Constraint.Size? = null,
) : Layout(constraints, gap) {

    override fun prePosition() {
        val gap = gap?.calculateSize(this, horizontal = false) ?: 0f
        var increment = 0f

        children?.forEach {
            if (it.constraints.y.undefined() && it.enabled) {
                it.internalY = increment

                val targetGap = if (it is Divider) 0f else gap

                // if the element is shrinking (animating) and becomes smaller than the gap shrink the gap with it. it fixes the bug where the animation isn't being played on gaps, I think
                val actualGap = if (it.height < targetGap) it.height else targetGap

                increment += it.height + actualGap
            }
        }
    }

    companion object {
        /**
         * Creates a row, with a width of [Copying] and height being specified.
         *
         * Acts as a section, to place elements in.
         */
        @AbobaDSL
        fun ElementScope<Column>.sectionRow(
            size: Constraint.Size = Bounding,
            gap: Constraint.Size? = null,
            block: ElementScope<Row>.() -> Unit
        ) = Row(size(w = Copying, h = size), gap).scope(block)
    }
}