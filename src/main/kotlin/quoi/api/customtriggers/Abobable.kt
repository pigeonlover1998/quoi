package quoi.api.customtriggers

import quoi.api.abobaui.elements.ElementScope

interface Abobable {
    fun displayString(): String = "Placeholder"
    fun ElementScope<*>.draw(): ElementScope<*>
}