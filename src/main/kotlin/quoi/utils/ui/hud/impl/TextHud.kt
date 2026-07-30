package quoi.utils.ui.hud.impl

import quoi.api.abobaui.constraints.impl.positions.Alignment
import quoi.api.abobaui.dsl.percent
import quoi.api.abobaui.elements.impl.Group
import quoi.api.abobaui.elements.impl.layout.Column
import quoi.api.colour.Colour
import quoi.module.Module
import quoi.module.settings.impl.SwitchComponent
import quoi.module.settings.impl.ColourPickerComponent
import quoi.module.settings.impl.SegmentedComponent
import quoi.module.settings.impl.SelectorComponent
import quoi.utils.ui.data.Anchor
import quoi.utils.ui.data.TextAlignment
import quoi.utils.ui.hud.Hud
import quoi.utils.ui.hud.ScopedHud
import quoi.utils.ui.rendering.Font
import quoi.utils.ui.rendering.NVGRenderer.customFont
import quoi.utils.ui.rendering.NVGRenderer.minecraftFont
import quoi.api.abobaui.elements.Element as AbobaElement

class TextHud(
    name: String,
    module: Module,
    toggleable: Boolean,
    val colourSetting: ColourPickerComponent,
    val shadowSetting: SwitchComponent,
    val fontSetting: SegmentedComponent<HudFont>?,
    val anchorSetting: SelectorComponent<Anchor>?,
    val alignmentSetting: SelectorComponent<TextAlignment>?,
    content: Scope.() -> Unit
) : ScopedHud<TextHud.Scope>(name, module, toggleable, content) {

    private val anchor: Anchor get() = anchorSetting?.selected ?: Anchor.TopLeft
    private val alignment: TextAlignment get() = alignmentSetting?.selected ?: TextAlignment.Left
    private val font: Font get() = (fontSetting?.selected ?: HudFont.Minecraft).get()

    private var alignmentParent: AbobaElement? = null

    class Scope(parent: Hud.Scope, val font: Font, val colour: Colour, val shadow: Boolean)
        : Hud.Scope(parent.element, parent.preview)

    override fun createScope(base: Hud.Scope): Scope {
        val anchor = /*anchorSetting.selected*/ anchor
        val element = base.element

        anchorSetting?.onValueChanged { _, _ ->
            if (element.ui.initialised) {
                savePosition(element, element.ui.main.width, element.ui.main.height)
                base.rebuildHuds()
            }
        }

        alignmentSetting?.onValueChanged { _, _ ->
            if (element.ui.initialised) {
                base.rebuildHuds()
            }
        }

        if (alignmentSetting != null && alignmentParent != element) {
            alignmentParent = element
            base.operation {
                if (align(element, alignment)) element.redraw()
                false
            }
        }

        element.constraints.x = Alignment.Relative(x.value.percent, anchor.x)
        element.constraints.y = Alignment.Relative(y.value.percent, anchor.y)

        return Scope(base, font, colourSetting.value, shadowSetting.value)
    }

    private fun align(parent: AbobaElement, alignment: TextAlignment): Boolean {
        var changed = false
        parent.children?.forEach { child ->
            val x = child.constraints.x
            if (x.undefined() || TextAlignment.entries.any { it.position == x }) {
                if (x != alignment.position) {
                    child.constraints.x = alignment.position
                    changed = true
                }
            }
            if (child is Group || child is Column) {
                changed = align(child, alignment) || changed
            }
        }
        return changed
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