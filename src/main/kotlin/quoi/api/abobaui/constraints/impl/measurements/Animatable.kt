package quoi.api.abobaui.constraints.impl.measurements

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.elements.Element
import quoi.api.animations.Animation

class Animatable(
    var from: Constraint,
    var to: Constraint,
) : Constraint.Measurement {

    constructor(from: Constraint, to: Constraint, swapIf: Boolean) : this(from, to,) {
        if (swapIf) {
            swap()
        }
    }

    var animation: Animation? = null
        private set

    private var current: Float = 0f

    private var before: Float? = null

    override fun calculate(element: Element, type: Int): Float {
        if (animation != null) {
            element.redraw()
            val progress = animation!!.get()
            val from = before ?: from.calculate(element, type)
            current = from + (to.calculate(element, type) - from) * progress

            if (animation!!.finished) {
                animation = null
                before = null
                swap()
            }
            return current
        }
        return from.calculate(element, type)
    }

    override fun reliesOnChildren() = from.reliesOnChildren() || to.reliesOnChildren()

    fun animate(duration: Float, style: Animation.Style): Animation? {
        if (duration == 0f) {
            swap()
            return null
        }

        animation = if (animation != null) {
            before = current
            swap()
            Animation(duration * animation!!.get(), style)
        } else {
            Animation(duration, style)
        }
        return animation
    }

    fun swap() {
        val temp = to
        to = from
        from = temp
    }

    class Raw(start: Float) : Constraint.Measurement {

        private var animation: Animation? = null

        private var from: Float = start
        private var to: Float = 0f

        private var current: Float = start

        fun animate(to: Float, duration: Float, style: Animation.Style) {
            if (animation == null) {
                if (duration == 0f) {
                    current = to
                    from = to
                } else {
                    this.from = current
                    this.to = to
                    animation = Animation(duration, style)
                }
            } else {
                if (duration != 0f) {
                    this.from = current
                    animation!!.restart(duration, style)
                }
                this.to = to
            }
        }

        fun to(to: Float) = if (animation != null) this.to = to else current = to

        override fun calculate(element: Element, type: Int): Float {
            if (animation != null) {
                element.redraw()
                current = from + (to - from) * animation!!.get()
                if (animation!!.finished) animation = null
            }
            return current
        }
    }
}