package quoi.utils.ui.screens

import quoi.api.abobaui.AbobaUI
import quoi.api.events.GuiEvent
import quoi.api.events.PacketEvent
import quoi.api.events.core.EventBus.on
import quoi.api.input.CatKeyboard.Modifier.isCtrlDown
import quoi.api.input.CatKeys
import quoi.utils.equalsOneOf
import quoi.utils.height
import quoi.utils.ui.rendering.NVGSpecialRenderer
import quoi.utils.width
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import quoi.api.input.CatMouse

class UIContainer(ui: AbobaUI.Instance, val cancelling: Boolean = true) : UIHandler(ui) {

    constructor(ui: AbobaUI, cancelling: Boolean = true) : this(AbobaUI.Instance(ui), cancelling)

    override val events = listOf(

        on<GuiEvent.Draw> {
            resize(width, height)

            ui.ctx = ctx
            mouseMove(CatMouse.mx, CatMouse.my)

            NVGSpecialRenderer.draw(ctx, 0, 0, ctx.guiWidth(), ctx.guiHeight()) {
                ui.render()
            }
            if (cancelling) cancel()
        },

        on<GuiEvent.Click> {
            if (state) mouseClick(button) else mouseRelease(button)
            if (cancelling) cancel()
        },

        on<GuiEvent.Key> {
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

        on<GuiEvent.Char> {
            charTyped(char)
        },

        on<GuiEvent.Close> {
            close()
        },

        on<PacketEvent.Received> {
            if (packet is ClientboundContainerClosePacket) close()
        },

        on<GuiEvent.DrawBackground> {
            if (cancelling) cancel()
        }
    )
}