package quoi.api.abobaui.elements

import quoi.api.abobaui.constraints.Constraints

abstract class BlankElement(constraints: Constraints) : Element(constraints) {
    final override fun draw() {  }
}