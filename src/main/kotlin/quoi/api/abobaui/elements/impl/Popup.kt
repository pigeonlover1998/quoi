package quoi.api.abobaui.elements.impl

import quoi.api.abobaui.constraints.Constraints
import quoi.api.abobaui.dsl.seconds
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.transforms.impl.Alpha
import quoi.api.abobaui.transforms.impl.Scale
import quoi.api.animations.Animation

/**
 * # Popup
 *
 * Extension of [ElementScope], which contains functions for a popup that can be closed with an animation.
 *
 * To create one use [popup]
 */
class Popup(
    element: Group,
    private val alphaAnimation: Alpha.Animated,
    private val scaleAnimation: Scale.Animated,
    private val smooth: Boolean
) : ElementScope<Group>(element) {

    private var closing = false

    /**
     * Closes this popup.
     *
     * @param smooth If this popup should animate before closing.
     */
    fun closePopup(smooth: Boolean = this.smooth) {
        if (!closing) {
            closing = true
            val length = if (!smooth) 0f else 0.25.seconds
            alphaAnimation.animate(length, Animation.Style.EaseInQuint)
            val anim = scaleAnimation.animate(length, Animation.Style.EaseInQuint)

            element.ui.addOperation {
                val finished = anim == null || anim.finished
                if (finished) element.ui.main.removeElement(element)
                finished
            }
        }
    }
}

/**
 * Function for creating [Popups][Popup].
 *
 * NOTE: It adds it to the root element inside a UI, and not the current scope.
 *
 * @param smooth If this popup should animate when opening (and closing).
 */
inline fun ElementScope<*>.popup(
    constraints: Constraints,
    smooth: Boolean,
    block: Popup.() -> Unit
): Popup {

    val alphaAnimation = Alpha.Animated(from = 0f, to = 1f)
    val scaleAnimation = Scale.Animated(from = 0f, to = 1f, centered = true)

    val group = Group(constraints).apply {
        addTransform(alphaAnimation)
        addTransform(scaleAnimation)
    }

    ui.main.addElement(group)

    val popup = Popup(group, alphaAnimation, scaleAnimation, smooth)
    popup.block()
    group.init()

    if (smooth) {
        alphaAnimation.animate(0.25.seconds, Animation.Style.EaseOutQuint)
        scaleAnimation.animate(0.25.seconds, Animation.Style.EaseOutQuint)
    } else {
        alphaAnimation.swap()
        scaleAnimation.swap()
    }
    return popup
}