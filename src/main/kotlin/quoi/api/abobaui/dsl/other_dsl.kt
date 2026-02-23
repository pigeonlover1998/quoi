package quoi.api.abobaui.dsl

import quoi.api.abobaui.AbobaUI
import quoi.api.abobaui.elements.Element
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Group
import quoi.api.abobaui.events.AbobaEvent
import quoi.api.abobaui.transforms.impl.Alpha
import quoi.api.abobaui.transforms.impl.Rotation
import quoi.api.abobaui.transforms.impl.Scale
import quoi.utils.render.DrawContextUtils.withMatrix
import quoi.utils.sf
import quoi.utils.ui.data.Radii

fun aboba(title: String = "", block: ElementScope<Group>.() -> Unit): AbobaUI.Instance {
//    val ui = AbobaUI(title)
//    ElementScope(ui.main).block()
//    return ui
    return AbobaUI.Instance(title, block)
}

fun aloba(block: ElementScope<Group>.() -> Unit): AbobaUI {
    val ui = AbobaUI("")
    ElementScope(ui.main).block()
    return ui
}

val Number.seconds: Float
    get() = this.toFloat() * 1_000_000_000


val Number.ms: Float
    get() = this.toFloat() * 1_000_000

fun Number.radius() = Radii(this.toFloat(), this.toFloat(), this.toFloat(), this.toFloat())

fun radius(tl: Number = 0f, bl: Number = 0f, br: Number = 0f, tr: Number = 0f) =
    Radii(tl.toFloat(), bl.toFloat(), br.toFloat(), tr.toFloat())

fun ElementScope<*>.scale(amount: Float): Scale {
    val scale = Scale(amount)
    transform(scale)
    return scale
}

fun ElementScope<*>.scale(from: Float, to: Float): Scale.Animated {
    val scale = Scale.Animated(from, to)
    transform(scale)
    return scale
}

fun ElementScope<*>.rotation(amount: Float): Rotation {
    val rotation = Rotation(amount)
    transform(rotation)
    return rotation
}

fun ElementScope<*>.rotation(from: Float, to: Float): Rotation.Animated {
    val rotation = Rotation.Animated(from, to)
    transform(rotation)
    return rotation
}

fun ElementScope<*>.alpha(amount: Float): Alpha {
    val alpha = Alpha(amount)
    transform(alpha)
    return alpha
}

fun ElementScope<*>.alpha(from: Float, to: Float): Alpha.Animated {
    val alpha = Alpha.Animated(from, to)
    transform(alpha)
    return alpha
}

fun Element.moveToTop() {
    val it = this
    this.parent!!.children!!.apply {
        remove(it)
        add(it)
    }
}

fun Element.moveToBottom() {
    val it = this
    this.parent!!.children!!.apply {
        remove(it)
        add(0, it)
    }
}

fun Element.withScale(block: () -> Unit) {
    val scaledElement = generateSequence(parent) { it.parent }
        .firstOrNull { it.scaleX != 1f } ?: this

    val scale = scaledElement.scaleX

    val dx = x - scaledElement.x
    val dy = y - scaledElement.y
    val screenX = scaledElement.x + dx * scale
    val screenY = scaledElement.y + dy * scale

    ctx.withMatrix {
        ctx.pose().identity()
        ctx.pose().scale(1f / sf, 1f / sf)
        ctx.pose().translate(screenX, screenY)
        ctx.pose().scale(scale)

        block()
    }
}



fun ElementScope<*>.passEvent(event: AbobaEvent, to: ElementScope<*>) {
    ui.eventManager.postToAll(event, to.element)
}

fun <E : Element> ElementScope<E>.toggle(): ElementScope<E> {
    element.enabled = !element.enabled
    return this
}

fun ElementScope<*>.focused(): Boolean {
    return ui.eventManager.focused == this.element
}