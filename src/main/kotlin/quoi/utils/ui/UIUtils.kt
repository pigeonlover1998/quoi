package quoi.utils.ui

import quoi.QuoiMod.mc
import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.Positions
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.Element
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.abobaui.elements.impl.TextInput
import quoi.api.colour.Colour
import quoi.api.input.CatMouse
import quoi.api.input.CursorShape
import quoi.module.settings.UIComponent
import quoi.utils.ui.rendering.Font
import quoi.utils.ui.rendering.NVGRenderer.minecraftFont
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

inline val inHudEditor get() = mc.screen?.title?.string == "Quoi! hud editor"

inline fun ElementScope<*>.onHover(duration: Float, crossinline block: () -> Unit) {
    onMouseEnter {
        val time = System.nanoTime()
        operation {
            if (System.nanoTime() - time >= duration) {
                block()
                return@operation true
            }
            !element.isInside(ui.mx, ui.my) || !element.renders
        }
    }
}

fun ElementScope<*>.cursor(shape: Long) {
    onMouseEnter {
        CatMouse.setCursor(shape)
    }

    onMouseExit {
        CatMouse.setCursor(CursorShape.NORMAL)
    }

    onRemove {
        CatMouse.setCursor(CursorShape.NORMAL)
    }
}

fun ElementScope<*>.delegateClick(input: ElementScope<TextInput>) {
    onClick {
        if (!input.focused()) {
            ui.focus(input.element)
        }
        true
    }
}

inline fun <T> ElementScope<*>.watch(
    crossinline supplier: () -> T,
    immediate: Boolean = false,
    crossinline block: (T) -> Unit,
) {
    var previous = supplier()
    if (immediate) block(previous)
    operation {
        supplier().let { current ->
            if (current != previous) {
                previous = current
                block(current)
            }
        }
        false
    }
}

inline fun <T> ElementScope<*>.watch(property: KProperty0<T>, immediate: Boolean = false, crossinline block: (T) -> Unit) =
    watch(property::get, immediate, block)


inline fun ElementScope<*>.textPair(
    string: String,
    crossinline supplier: () -> Any?,
    labelColour: Colour,
    valueColour: Colour = Colour.WHITE,
    shadow: Boolean,
    font: Font = minecraftFont,
    pos: Positions = at(),
    size: Constraint.Size = 18.px
) = row(pos) {
    text(
        string = "$string ",
        font = font,
        size = size,
        colour = labelColour
    ).shadow = shadow
    textSupplied(
        supplier = supplier,
        font = font,
        size = size,
        colour = valueColour
    ).shadow = shadow
}

fun ElementScope<*>.popupX(gap: Float = 5f): Constraint.Position {
    return object : Constraint.Position {
        override fun calculatePos(element: Element, horizontal: Boolean): Float {
            val x = this@popupX.element.x
            val sw = this@popupX.element.screenWidth()

            return if (x + sw + element.width + gap * 2 >= ui.main.width) {
                x - gap - element.width
            } else {
                x + sw + gap
            }
        }
    }
}

fun ElementScope<*>.popupY(gap: Float = 0f, corner: Boolean = false): Constraint.Position {
    return object : Constraint.Position {
        override fun calculatePos(element: Element, horizontal: Boolean): Float {
            val y = this@popupY.element.y
            val sh = this@popupY.element.screenHeight()

            return if (y + element.screenHeight() + gap * 2 > ui.main.height) {
                y - gap - element.height + if (corner) 0f else sh
            } else {
                y + gap + if (corner) sh else 0f
            }
        }
    }
}

fun settingFromK0(property: KProperty0<*>): UIComponent<*> {
    property.isAccessible = true
    return property.getDelegate() as? UIComponent<*> ?: throw Exception("no good")
}