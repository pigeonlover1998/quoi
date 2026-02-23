package quoi.api.abobaui.constraints

open class Constraints(
    var x: Constraint.Position,
    var y: Constraint.Position,
    var width: Constraint.Size,
    var height: Constraint.Size,
) {
    fun getPosition(horizontal: Boolean) = if (horizontal) x else y

    fun getSize(horizontal: Boolean) = if (horizontal) width else height

    fun sizeReliesOnChildren() = width.reliesOnChildren() || height.reliesOnChildren()
}