package quoi.api.abobaui.transforms.impl

import quoi.api.abobaui.elements.Element
import quoi.api.abobaui.transforms.Transform
import quoi.utils.ui.rendering.NVGRenderer

/**
 * # Scale
 *
 * This transformation scales an element by a provided amount.
 *
 * This class can be delegated to a float because of [Transform.Mutable].
 *
 * @param centered If scaling should scale from center or from top-left corner
 */
class Scale(
    override var amount: Float,
    private val centered: Boolean = true
) : Transform.Mutable {

    override fun apply(element: Element) {
        scale(element, amount, amount, centered)
    }

    /**
     * # Scale.Animated
     *
     * This transformation scales an element by an amount, which can be animated.
     *
     * @param centered If scaling should scale from center or from top-left corner
     */
    class Animated(
        from: Float,
        to: Float,
        private val centered: Boolean = true
    ) : Transform.Animated(from, to) {
        override fun apply(element: Element) {
            val amount = get()
            scale(element, amount, amount, centered)
        }
    }
}

// utility fun
private fun scale(element: Element, amountX: Float, amountY: Float, centered: Boolean) {
    var x = element.x
    var y = element.y
    if (centered) {
        x += element.width / 2f
        y += element.height / 2f
    }
    element.ctx.pose().translate(x, y)
    element.ctx.pose().scale(amountX, amountY)
    element.ctx.pose().translate(-x, -y)
    NVGRenderer.translate(x, y)
    NVGRenderer.scale(amountX, amountY)
    NVGRenderer.translate(-x, -y)
    element.scaleX = amountX
    element.scaleY = amountY
}