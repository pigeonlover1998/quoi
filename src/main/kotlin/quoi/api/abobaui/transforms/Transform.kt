package quoi.api.abobaui.transforms

import quoi.api.abobaui.elements.Element
import quoi.api.animations.Animation
import kotlin.reflect.KProperty

fun interface Transform {

    fun apply(element: Element)

    interface Mutable : Transform {

        var amount: Float

        operator fun getValue(thisRef: Element, property: KProperty<*>): Float {
            return amount
        }

        operator fun setValue(thisRef: Element, property: KProperty<*>, value: Float) {
            amount = value
        }
    }

    abstract class Animated(
        private var from: Float,
        private var to: Float,
    ) : Transform {

        var animation: Animation? = null
            private set

        private var current: Float = 0f
        private var before: Float? = null

        protected fun get(): Float {
            animation?.let { anim ->
                val progress = anim.get()
                val from = before ?: from
                current = from + (to - from) * progress

                if (anim.finished) {
                    animation = null
                    before = null
                    swap()
                }
                return current
            }
            return from
        }

        // todo: maybe implement common class for this, Animatable and Color.Animated
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
    }
}