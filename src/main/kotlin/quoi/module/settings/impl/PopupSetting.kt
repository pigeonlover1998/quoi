package quoi.module.settings.impl

import quoi.api.abobaui.constraints.impl.measurements.Undefined
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.abobaui.elements.impl.Popup
import quoi.api.abobaui.elements.impl.popup
import quoi.api.colour.Colour
import quoi.utils.ThemeManager.theme
import quoi.module.settings.UISetting

class PopupSetting(
    name: String,
    desc: String = "",
    val smooth: Boolean = false,
    val draggable: Boolean = false,
    val middle: Boolean = false,
    override val default: ElementScope<*>.() -> Unit
) : UISetting<ElementScope<*>.() -> Unit>(name, desc) {
    
    override var value: ElementScope<*>.() -> Unit = default

    private var popup: Popup? = null

    override fun ElementScope<*>.draw(asSub: Boolean): ElementScope<*> = row(size(w = Copying)) {
        text(
            string = name,
            size = theme.textSize,
            colour = theme.textSecondary,
            pos = at(y = Centre)
        )
        block( // todo replace with image
            constrain(x = 0.px.alignOpposite, w = 25.px, h = 25.px),
            colour = Colour.BLACK
        ) {
            onClick {
                popup?.closePopup()
                val e = this@block.element
                val x = if (middle) Undefined else  (e.x + e.width).px
                val y = if (middle) Undefined else (e.y + e.height).px
                popup = popup(copies(), smooth = smooth) {
                    if (draggable) draggable()

                    onClick {
                        closePopup()
                        popup = null
                    }

                    block(
                        constrain(
                            x = x,
                            y = y,
                            w = Bounding + 6.px,
                            h = Bounding + 6.px
                        ),
                        colour = theme.background,
                        5.radius()
                    ) {
                        outline(theme.border, thickness = 2.px)
                        onClick { true }

                        value()
                    }
                }
            }
        }
    }

}