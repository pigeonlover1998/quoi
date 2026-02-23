package quoi.utils.ui.hud.impl

import quoi.api.abobaui.constraints.impl.positions.Alignment
import quoi.api.abobaui.dsl.percent
import quoi.api.colour.Colour
import quoi.module.Module
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.ColourSetting
import quoi.module.settings.impl.SelectorSetting
import quoi.utils.ui.data.Anchor
import quoi.utils.ui.hud.Hud
import quoi.utils.ui.hud.ScopedHud

class TextHud(
    name: String,
    module: Module,
    toggleable: Boolean,
    val colourSetting: ColourSetting,
    val shadowSetting: BooleanSetting,
    val anchorSetting: SelectorSetting<Anchor>,
    content: Scope.() -> Unit
) : ScopedHud<TextHud.Scope>(name, module, toggleable, content) {

    class Scope(parent: Hud.Scope, val colour: Colour, val shadow: Boolean)
        : Hud.Scope(parent.element, parent.preview)

    override fun createScope(base: Hud.Scope): Scope {
        val anchor = anchorSetting.selected
        val element = base.element

        anchorSetting.onValueChanged { _, _ ->
            savePosition(element, element.ui.main.width, element.ui.main.height)
            base.rebuildHuds()
        }

        element.constraints.x = Alignment.Relative(x.value.percent, anchor.x)
        element.constraints.y = Alignment.Relative(y.value.percent, anchor.y)

        return Scope(base, colourSetting.value, shadowSetting.value)
    }

    override fun savePosition(element: Element, screenWidth: Float, screenHeight: Float) {
        val anchor = anchorSetting.selected

        val targetX = element.x + (element.width * anchor.x)
        val targetY = element.y + (element.height * anchor.y)

        x.value = (targetX / screenWidth) * 100f
        y.value = (targetY / screenHeight) * 100f
    }
}