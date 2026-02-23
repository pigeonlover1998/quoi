package quoi.module.settings

import quoi.api.abobaui.constraints.impl.measurements.Animatable
import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.dsl.seconds
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.events.AbobaEvent
import quoi.api.abobaui.transforms.impl.Alpha
import quoi.api.animations.Animation

abstract class UISetting<T>(
    name: String,
    desc: String,
) : Setting<T>(name, desc) {

    abstract fun ElementScope<*>.draw(asSub: Boolean = isSubsetting): ElementScope<*>

    val isSubsetting get() = parent != null

    private var onValueChanged: (old: T, new: T) -> Unit = { _, _ -> }

    fun onValueChanged(action: (old: T, new: T) -> Unit): Setting<T> {
        onValueChanged = action
        return this
    }

    fun render(scope: ElementScope<*>, asSub: Boolean = isSubsetting): ElementScope<*> {
        val rendering = scope.draw(asSub)
        val element = rendering.element
        var hashCode = value.hashCode()
        var oldValue = value

        val alphaAnimation = Alpha.Animated(to = 0f, from = 1f)
        var visible = isVisible

        element.addTransform(alphaAnimation)

        if (element.constraints.height !is Animatable/* && !isSubsetting*/) {
            element.constraints.height = Animatable(
                from = element.constraints.height,
                to = 0.px,
                swapIf = !visible
            )

            if (!visible) {
                alphaAnimation.swap()
            }
        }

        rendering.operation {

            if (visible != isVisible) {
                visible = isVisible
                (element.constraints.height as Animatable).animate(0.2.seconds, Animation.Style.EaseInOutQuint)
                alphaAnimation.animate(0.2.seconds, Animation.Style.EaseInOutQuint)
                element.parent?.redraw = true
            }

            if (hashCode != value.hashCode()) {
                hashCode = value.hashCode()
                onValueChanged.invoke(oldValue, value)
                oldValue = value
                rendering.ui.eventManager.postToAll(ValueUpdated, element)
            }
            false
        }
        return rendering
    }

    inline fun ElementScope<*>.onValueChanged(crossinline block: (ValueUpdated) -> Unit) {
        element.registerEvent(ValueUpdated) {
            block(it)
            element.redraw()
//            true
            false
        }
    }

    data object ValueUpdated : AbobaEvent
}