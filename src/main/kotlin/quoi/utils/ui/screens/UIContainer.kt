package quoi.utils.ui.screens

import net.minecraft.client.gui.GuiGraphicsExtractor
import quoi.api.abobaui.AbobaUI
import quoi.api.events.GuiEvent
import quoi.api.events.PacketEvent
import quoi.api.events.core.on
import quoi.api.input.CatKeyboard.Modifier.isCtrlDown
import quoi.api.input.CatKeys
import quoi.utils.equalsOneOf
import quoi.utils.height
import quoi.utils.ui.rendering.NVGSpecialRenderer
import quoi.utils.width
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import quoi.api.input.CatMouse.mx
import quoi.api.input.CatMouse.my

class UIContainer(ui: AbobaUI.Instance, val cancelling: Boolean = true) : UIHandler(ui) {

    constructor(ui: AbobaUI, cancelling: Boolean = true) : this(AbobaUI.Instance(ui), cancelling)

    private fun render(ctx: GuiGraphicsExtractor, cancel: () -> Unit) {
        resize(width, height)

        ui.ctx = ctx
        mouseMove(mx, my)

        NVGSpecialRenderer.draw(ctx, 0, 0, ctx.guiWidth(), ctx.guiHeight()) {
            ui.render(true)
        }
        ui.render(false)
        if (cancelling) cancel()
    }

    override val events = listOf(

        if (cancelling)
            on<GuiEvent.Draw>(register = false) { render(ctx, ::cancel) }
        else
            on<GuiEvent.DrawTooltip>(register = false) { render(ctx, ::cancel) },

        on<GuiEvent.Click>(register = false) {
            if (state) mouseClick(button) else mouseRelease(button)
            if (cancelling) cancel()
        },

        on<GuiEvent.Key.Press>(register = false) {
            keyTyped(key)

            val ctrlHotkeys = setOf(
                CatKeys.KEY_V,
                CatKeys.KEY_C,
                CatKeys.KEY_W,
                CatKeys.KEY_X,
                CatKeys.KEY_A
            )
            if (isCtrlDown && key in ctrlHotkeys) {
                charTyped(key.toChar())
            }

            if (!key.equalsOneOf(CatKeys.KEY_E, CatKeys.KEY_ESCAPE) && cancelling) cancel()
        },

        on<GuiEvent.Char>(register = false) {
            charTyped(char)
        },

        on<GuiEvent.Close>(register = false) {
            close()
        },

        on<PacketEvent.Received>(register = false) {
            if (packet is ClientboundContainerClosePacket) close()
        },

        on<GuiEvent.DrawBackground>(register = false) {
            if (cancelling) cancel()
        }
    )
}