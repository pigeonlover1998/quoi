package quoi.module.settings.impl

import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.animations.Animation
import quoi.utils.ThemeManager.theme
import quoi.module.settings.UISetting

class DropdownSetting(
    name: String,
    desc: String = "",
) : UISetting<DropdownSetting>(name, desc) {

    override val default = this
    override var value: DropdownSetting
        get() = default
        set(value) { collapsed = value.collapsed }

    val children: MutableList<UISetting<*>> = mutableListOf()

    var collapsed = false
    var collapsible = false

    // makes this dropdown collapsible
    fun collapsible(collapse: Boolean = true): DropdownSetting {
        collapsible = true
        collapsed = collapse
        return default
    }

    override fun hashCode() = collapsed.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DropdownSetting) return false
        return collapsed == other.collapsed
    }


    override fun ElementScope<*>.draw(asSub: Boolean): ElementScope<*> = column(size(w = Copying)) {
        row(size(w = Copying)) {
            text(
                string = name,
                size = theme.textSize,
                colour = theme.textSecondary,
                pos = at(y = Centre)
            )

            image(
                image = theme.chevronImage,
                constrain(0.px.alignOpposite, w = 24.px, h = 24.px)
            ) {
                val (from, to) = if (collapsed) 180f to 90f else 90f to 180f
                val rotation = rotation(from = from, to = to)

                onClick {
                    if (collapsible) value.collapsed = !value.collapsed
                }

                onValueChanged {
                    rotation.animate(0.25.seconds, Animation.Style.EaseInOutQuint)
                }
            }
        }
    }
}