package quoi.utils.ui.screens

import quoi.api.abobaui.AbobaUI
import quoi.api.events.core.EventBus.on
import quoi.api.events.GuiEvent
import quoi.api.events.RenderEvent
import quoi.api.input.CatMouse.mx
import quoi.api.input.CatMouse.my
import quoi.utils.height
import quoi.utils.width

class UIOverlay(ui: AbobaUI.Instance) : UIHandler(ui) {

    constructor(ui: AbobaUI) : this(AbobaUI.Instance(ui))

    override val events = listOf(

        on<RenderEvent.Overlay> {
            resize(width, height)
            ui.ctx = ctx
            mouseMove(mx, my)
            ui.render()
        },

        on<GuiEvent.Click> {
            if (state) mouseClick(button) else mouseRelease(button)
        },

        on<GuiEvent.Key> {
            keyTyped(key)
        }
    )
}