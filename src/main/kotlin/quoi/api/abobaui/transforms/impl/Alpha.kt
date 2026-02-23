package quoi.api.abobaui.transforms.impl

import quoi.api.abobaui.elements.Element
import quoi.api.abobaui.transforms.Transform
import quoi.utils.ui.rendering.NVGRenderer

class Alpha(override var amount: Float) : Transform.Mutable {
    override fun apply(element: Element) {
        NVGRenderer.globalAlpha(amount)
    }

    /**
     * # Alpha.Animated
     *
     * This transformation changes the alpha of an element by an amount, which can be animated.
     */
    class Animated(
        from: Float,
        to: Float,
    ) : Transform.Animated(from, to) {
        override fun apply(element: Element) {
            NVGRenderer.globalAlpha(get())
        }
    }
}