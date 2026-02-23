package quoi.utils.ui.hud

import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.module.Module
import quoi.module.settings.UISetting
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.ColourSetting
import quoi.module.settings.impl.HudSetting
import quoi.module.settings.impl.NumberSetting
import quoi.module.settings.impl.SelectorSetting
import quoi.utils.ChatUtils.literal
import quoi.utils.render.DrawContextUtils.withMatrix
import quoi.utils.scaledHeight
import quoi.utils.scaledWidth
import quoi.utils.sf
import quoi.utils.ui.data.Anchor
import quoi.utils.ui.hud.impl.ResizableHud
import quoi.utils.ui.hud.impl.TextHud
import net.minecraft.client.gui.GuiGraphics

fun <T : Hud> T.setting(desc: String = "") = HudSetting(name, this, desc)

fun <T : Hud> T.withTransform(ctx: GuiGraphics, block: GuiGraphics.() -> Unit) {
    if (mc.screen?.title?.equals(literal("Quoi! hud editor")) == true) return
    ctx.withMatrix(
        x = x.value / 100f * scaledWidth,
        y = y.value / 100f * scaledHeight,
        scale = scale.value / sf
    ) {
        ctx.block()
    }
}

fun Module.Hud(name: String, toggleable: Boolean = true, block: Hud.Scope.() -> Unit) =
    Hud(name, this, toggleable, block)

fun Module.TextHud(
    name: String,
    colour: Colour = Colour.WHITE,
    toggleable: Boolean = true,
    block: TextHud.Scope.() -> Unit
): TextHud {
    val colourSetting = ColourSetting("Colour", colour)
    val shadowSetting = BooleanSetting("Shadow", true)
    val anchorSetting = SelectorSetting("Anchor", Anchor.TopLeft)

    val hud = TextHud(name, this, toggleable, colourSetting, shadowSetting, anchorSetting, block)

    hud.withSettings(colourSetting, shadowSetting, anchorSetting)
    return hud
}

fun Module.ResizableHud(
    name: String,
    width: Float = 100f,
    height: Float = 100f,
    colour: Colour = Colour.WHITE,
    outline: Colour? = null,
    thickness: Float = 2.0f,
    toggleable: Boolean = true,
    block: ResizableHud.Scope.() -> Unit
): ResizableHud {
    val width = NumberSetting("Width", width, 10f, 10000f, 1f).hide()
    val height = NumberSetting("Height", height, 10f, 10000f, 1f).hide()
    val colour = ColourSetting("Colour", colour)
    val outlineColour = if (outline != null) ColourSetting("Outline colour", outline) else null
    val outlineThickness = if (outline != null) NumberSetting("Outline thickness", thickness, 1f, 10f, increment = 1f) else null

    val hud = ResizableHud(name, this, toggleable, width, height, colour, outlineColour, outlineThickness, block)

    val settings = listOfNotNull<UISetting<*>>(width, height, colour, outlineColour, outlineThickness).toTypedArray()
    hud.withSettings(*settings)
    return hud
}