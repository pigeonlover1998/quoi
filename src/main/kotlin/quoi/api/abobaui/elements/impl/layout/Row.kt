package quoi.api.abobaui.elements.impl.layout

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.Constraints
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.size
import quoi.api.abobaui.elements.AbobaDSL
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.Layout

class Row(
    constraints: Constraints,
    gap: Constraint.Size? = null
) : Layout(constraints, gap) {

    override fun prePosition() {
        val gap = gap?.calculateSize(this, horizontal = true) ?: 0f
        var increment = 0f
        children?.forEach {
            if (it.constraints.x.undefined() && it.enabled) {
                it.internalX = increment
                increment += it.width + if (it is Divider) 0f else gap
            }
        }
    }

    companion object {
        /**
         * Creates a column, with a width being specified and height of [Copying].
         *
         * Acts as a section, to place elements in.
         */
        @AbobaDSL
        fun ElementScope<Column>.sectionColumn(
            size: Constraint.Size = Bounding,
            gap: Constraint.Size? = null,
            block: ElementScope<Column>.() -> Unit
        ) = Column(size(w = size, h = Copying), gap).scope(block)
    }
}