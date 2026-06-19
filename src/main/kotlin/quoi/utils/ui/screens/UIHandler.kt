package quoi.utils.ui.screens

import quoi.QuoiMod.mc
import quoi.api.abobaui.AbobaUI
import quoi.api.events.core.EventManager
import quoi.api.events.core.Subscription

abstract class UIHandler(val ui: AbobaUI.Instance) {
    protected var prevWidth = 0
    protected var prevHeight = 0

    abstract val events: List<Subscription<*>>

    open fun open() {
        ui.init(mc.window.width, mc.window.height)
        events.forEach { EventManager.register(it) }
    }

    open fun close() {
        ui.close()
        events.forEach { it.unregister() }
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
