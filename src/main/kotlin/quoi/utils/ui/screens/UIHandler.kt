package quoi.utils.ui.screens

import quoi.QuoiMod.mc
import quoi.api.abobaui.AbobaUI
import quoi.api.events.core.EventBus

abstract class UIHandler(val ui: AbobaUI.Instance) {
    protected var prevWidth = 0
    protected var prevHeight = 0

    abstract val events: List<EventBus.EventListener>

    open fun open() {
        ui.init(mc.window.width, mc.window.height)
        events.forEach { it.add() }
    }

    open fun close() {
        ui.close()
        events.forEach { it.remove() }
    }

    protected fun resize(currentWidth: Int, currentHeight: Int) {
        if (currentWidth != prevWidth || currentHeight != prevHeight) {
            ui.resize(currentWidth, currentHeight)
            prevWidth = currentWidth
            prevHeight = currentHeight
        }
    }

    protected fun mouseMove(x: Float, y: Float) = ui.eventManager.onMouseMove(x, y)
    protected fun mouseClick(button: Int) = ui.eventManager.onMouseClick(button)
    protected fun mouseRelease(button: Int) = ui.eventManager.onMouseRelease(button)
    protected fun keyTyped(key: Int) = ui.eventManager.onKeyTyped(key)
    protected fun charTyped(char: Char) = ui.eventManager.onKeyTyped(char)
}
