package quoi.module.settings.impl

import quoi.api.abobaui.constraints.impl.measurements.Animatable
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.animations.Animation
import quoi.api.input.CursorShape
import quoi.utils.ThemeManager.theme
import quoi.module.settings.UISetting
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.ui.cursor


class ActionSetting(
    name: String,
    desc: String = "",
    override val default: () -> Unit = {}
) : UISetting<() -> Unit>(name, desc) {

    override var value: () -> Unit = default

    var action: () -> Unit by this::value

    override fun ElementScope<*>.draw(asSub: Boolean): ElementScope<*> =
        block(
            size(w = Copying, h = 25.px),
            colour = theme.panel,
            5.radius()
        ) {
            val thickness = Animatable(from = 2.px, to = 3.px)
            outline(theme.accent, thickness = thickness)
            hoverEffect(factor = 1.15f)
            onClick {
                thickness.animate(0.25.seconds, style = Animation.Style.EaseInOutQuint)?.onFinish {
                    scheduleTask {
                        thickness.animate(0.25.seconds, Animation.Style.EaseInOutQuint)
                    }
                }
                action.invoke()
                true
            }
            text(
                string = name,
                size = theme.textSize,
                colour = theme.textSecondary
            )

            cursor(CursorShape.HAND)
        }
}