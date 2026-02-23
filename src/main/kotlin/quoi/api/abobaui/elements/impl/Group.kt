package quoi.api.abobaui.elements.impl

import quoi.api.abobaui.constraints.Constraints
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.elements.BlankElement
import quoi.api.abobaui.elements.ElementScope

open class Group(constraints: Constraints) : BlankElement(constraints) {
    init {
        if (constraints.width.undefined()) constraints.width = Bounding
        if (constraints.height.undefined()) constraints.height = Bounding
    }
}

open class RefreshableGroup(
    constraints: Constraints,
    private val builder: ElementScope<RefreshableGroup>.() -> Unit
) : Group(constraints) {

    fun refresh() {
        removeAll()
        val scope = ElementScope(this)
        scope.builder()
        redraw()
    }
}

fun ElementScope<*>.refreshableGroup(
    constraints: Constraints,
    block: ElementScope<RefreshableGroup>.() -> Unit = {}
): RefreshableGroup {
    val group = RefreshableGroup(constraints, block)
    this.element.addElement(group)
    group.refresh()
    return group
}