package quoi.utils.ui.hud

import net.minecraft.client.gui.GuiGraphics
import quoi.api.colour.Colour
import quoi.module.Module
import quoi.module.settings.UIComponent
import quoi.module.settings.impl.*
import quoi.utils.render.DrawContextUtils.withMatrix
import quoi.utils.scaledHeight
import quoi.utils.scaledWidth
import quoi.utils.sf
import quoi.utils.ui.data.Anchor
import quoi.utils.ui.hud.impl.ResizableHud
import quoi.utils.ui.hud.impl.TextHud
import quoi.utils.ui.inHudEditor

interface HudDSL {
    fun <T : Hud> T.setting(desc: String = "") = HudComponent(name, this, desc)

    fun <T : Hud> T.withTransform(ctx: GuiGraphics, block: GuiGraphics.() -> Unit) {
        if (inHudEditor) return
        ctx.withMatrix(
            x = x.value / 100f * scaledWidth,
            y = y.value / 100f * scaledHeight,
            scale = scale.value / sf,
            block = { ctx.block() }
        )
    }

    fun hud(name: String, toggleable: Boolean = true, block: Hud.Scope.() -> Unit) =
        Hud(name, this as Module, toggleable, block)

    fun textHud(
        name: String,
        colour: Colour = Colour.WHITE,
        font: TextHud.HudFont? = TextHud.HudFont.Minecraft,
        anchor: Anchor? = Anchor.TopLeft,
        toggleable: Boolean = true,
        block: TextHud.Scope.() -> Unit
    ): TextHud {
        val colourSetting = ColourPickerComponent("Colour", colour)
        val shadowSetting = SwitchComponent("Shadow", true)
        val fontSetting = font?.let { SegmentedComponent("Font", it) }
        val anchorSetting = anchor?.let { SelectorComponent("Anchor", it) }

        val hud = TextHud(name, this as Module, toggleable, colourSetting, shadowSetting, fontSetting, anchorSetting, block)

        val settings = listOfNotNull<UIComponent<*>>(colourSetting, shadowSetting, fontSetting, anchorSetting).toTypedArray()
        hud.withSettings(*settings)
        return hud
    }

    fun resizableHud(
        name: String,
        width: Float = 100f,
        height: Float = 100f,
        colour: Colour = Colour.WHITE,
        outline: Colour? = null,
        thickness: Float = 2.0f,
        toggleable: Boolean = true,
        block: ResizableHud.Scope.() -> Unit
    ): ResizableHud {
        val width = SliderComponent("Width", width, 10f, 10000f, 1f).hide()
        val height = SliderComponent("Height", height, 10f, 10000f, 1f).hide()
        val colour = ColourPickerComponent("Colour", colour, allowAlpha = true)
        val outlineColour = outline?.let { ColourPickerComponent("Outline colour", it, allowAlpha = true) }
        val outlineThickness = outline?.let { SliderComponent("Outline thickness", thickness, 1f, 10f, increment = 0.5f) }

        val hud = ResizableHud(name, this as Module, toggleable, width, height, colour, outlineColour, outlineThickness, block)

        val settings = listOfNotNull<UIComponent<*>>(width, height, colour, outlineColour, outlineThickness).toTypedArray()
        hud.withSettings(*settings)
        return hud
    }
}