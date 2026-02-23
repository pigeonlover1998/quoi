package quoi.api.abobaui.events

import quoi.api.abobaui.AbobaUI
import quoi.api.abobaui.elements.Element
import kotlin.math.sign

class EventManager(private val ui: AbobaUI) {
    var mouseX: Float = 0f
    var mouseY: Float = 0f
    var mouseDown: Boolean = false

    private var hoveredElement: Element? = null

    var focused: Element? = null
        set(value) {
            if (field == value) return
            field?.acceptFocused(Focus.Lost)
            value?.acceptFocused(Focus.Gained)
            field = value
        }

    fun onMouseMove(x: Float, y: Float) {
        if (mouseX == x && mouseY == y) return
        mouseX = x
        mouseY = y
        hoveredElement = getHovered(x, y)
        postToAll(Mouse.Moved)
    }

    fun onMouseClick(button: Int): Boolean {
        mouseDown = true
        hoveredElement?.pressed = true
        val eventNS = Mouse.Clicked.NonSpecific(button)
        val event = Mouse.Clicked(button)

        if (focused != null) {
            if (focused!!.isInside(mouseX, mouseY)) {
                if (focused!!.accept(eventNS)) {
                    return true
                }
                return focused?.accept(event) ?: false
            } else {
                focused = null
            }
        }
        return post(eventNS) || post(event)
    }

    fun onMouseRelease(button: Int) {
        mouseDown = false
        hoveredElement?.pressed = false
        postToAll(Mouse.Released(button))
    }

    fun onMouseScroll(amount: Float): Boolean {
        return post(Mouse.Scrolled(amount.sign.toInt()))
    }

    fun onKeyTyped(char: Char): Boolean {
        if (focused != null) {
            return focused!!.acceptFocused(Keyboard.CharTyped(char))
        }
        return false
    }

    fun onKeyTyped(key: Int): Boolean {
        if (focused != null) {
            return focused!!.acceptFocused(Keyboard.KeyTyped(key))
        }
        return ui.main.accept(Keyboard.KeyTyped(key)) // idk
    }

    fun onKeyReleased(key: Int): Boolean {
        if (focused != null) {
            return focused!!.acceptFocused(Keyboard.KeyReleased(key))
        }
        return false
    }

    fun post(event: AbobaEvent, element: Element? = hoveredElement): Boolean {
        var current = element
        while (current != null) {
            if (current.accept(event)) {
                return true
            }
            current = current.parent
        }
        return false
    }

    fun postToAll(event: AbobaEvent, element: Element = ui.main) {
        element.accept(event)
        element.children?.forEach { postToAll(event, it) }
    }

    fun recalculate() {
        hoveredElement = getHovered(mouseX, mouseY)
    }

    private fun getHovered(x: Float, y: Float, element: Element = ui.main): Element? {
        var result: Element? = null

        if (element.renders && element.isInside(x, y)) {
            element.children?.reversed()?.forEach { it ->
                if (result == null) {
                    getHovered(x, y, it)?.let {
                        result = it
                        return@forEach
                    }
                }
                unmarkHovered(it)
            }
            if (element.acceptsInput) {
                element.hovered = true
                if (result == null) result = element
            }
        }
        return result
    }

    private fun unmarkHovered(element: Element) {
        if (!element.hovered && element.acceptsInput) return
        element.hovered = false
        element.children?.forEach { unmarkHovered(it) }
    }
}