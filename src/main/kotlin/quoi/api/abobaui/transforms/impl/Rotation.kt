package quoi.api.abobaui.transforms.impl

import quoi.api.abobaui.elements.Element
import quoi.api.abobaui.transforms.Transform
import quoi.utils.rad
import quoi.utils.ui.rendering.NVGRenderer

class Rotation(override var amount: Float) : Transform.Mutable {

    override fun apply(element: Element) {
        rotate(element, amount)
    }

    /**
     * # Scale.Animated
     *
     * This transformation rotates an element by an amount, which can be animated.
     */
    class Animated(
        from: Float,
        to: Float,
    ) : Transform.Animated(from, to) {
        override fun apply(element: Element) {
            rotate(element, get())
        }
    }

    class Dynamic(private val amount: () -> Float) : Transform {
        override fun apply(element: Element) {
            rotate(element, amount())
        }
    }
}

private fun rotate(element: Element, amount: Float) {
    val x = element.x + element.width / 2f
    val y = element.y + element.height / 2f
    val ang = amount.rad

    if (!element.ui.nvgPass) {
        element.ctx.pose().translate(x, y)
        element.ctx.pose().rotate(ang)
        element.ctx.pose().translate(-x, -y)
    } else {
        NVGRenderer.translate(x, y)
        NVGRenderer.rotate(ang)
        NVGRenderer.translate(-x, -y)
    }
}