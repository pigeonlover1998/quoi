package quoi.api.abobaui.constraints.impl.size

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.elements.Element

class AspectRatio(
    private var ratio: Float
) : Constraint.Size {

    override fun calculateSize(element: Element, horizontal: Boolean): Float {
        var size = element.getSize(!horizontal)
        size = if (horizontal) size * ratio else size / ratio
        return size
    }
}