package quoi.utils.ui.hud.impl

import quoi.api.abobaui.constraints.impl.positions.Alignment
import quoi.api.abobaui.dsl.percent
import quoi.api.colour.Colour
import quoi.module.Module
import quoi.module.settings.impl.SwitchComponent
import quoi.module.settings.impl.ColourPickerComponent
import quoi.module.settings.impl.SegmentedComponent
import quoi.module.settings.impl.SelectorComponent
import quoi.utils.ui.data.Anchor
import quoi.utils.ui.hud.Hud
import quoi.utils.ui.hud.ScopedHud
import quoi.utils.ui.rendering.Font
import quoi.utils.ui.rendering.NVGRenderer.customFont
import quoi.utils.ui.rendering.NVGRenderer.minecraftFont

class TextHud(
    name: String,
    module: Module,
    toggleable: Boolean,
    val colourSetting: ColourPickerComponent,
    val shadowSetting: SwitchComponent,
    val fontSetting: SegmentedComponent<HudFont>?,
    val anchorSetting: SelectorComponent<Anchor>?,
    content: Scope.() -> Unit
) : ScopedHud<TextHud.Scope>(name, module, toggleable, content) {

    private val anchor: Anchor get() = anchorSetting?.selected ?: Anchor.TopLeft
    private val font: Font get() = (fontSetting?.selected ?: HudFont.Minecraft).get()

    class Scope(parent: Hud.Scope, val font: Font, val colour: Colour, val shadow: Boolean)
        : Hud.Scope(parent.element, parent.preview)

    override fun createScope(base: Hud.Scope): Scope {
        val anchor = /*anchorSetting.selected*/ anchor
        val element = base.element

        anchorSetting?.onValueChanged { _, _ ->
            savePosition(element, element.ui.main.width, element.ui.main.height)
            base.rebuildHuds()
        }

        element.constraints.x = Alignment.Relative(x.value.percent, anchor.x)
        element.constraints.y = Alignment.Relative(y.value.percent, anchor.y)

        return Scope(base, font, colourSetting.value, shadowSetting.value)
    }

    override fun savePosition(element: Element, screenWidth: Float, screenHeight: Float) {
        val anchor = /*anchorSetting.selected*/ anchor

        val targetX = element.x + (element.width * anchor.x)
        val targetY = element.y + (element.height * anchor.y)

        x.value = (targetX / screenWidth) * 100f
        y.value = (targetY / screenHeight) * 100f
    }

    enum class HudFont {
        Minecraft,
        Custom;

        fun get() = if (this == Minecraft) minecraftFont else customFont
    }
}