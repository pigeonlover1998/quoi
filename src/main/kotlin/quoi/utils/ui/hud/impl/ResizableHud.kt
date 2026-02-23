package quoi.utils.ui.hud.impl

import quoi.api.abobaui.constraints.impl.measurements.Pixel
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.colour.Colour
import quoi.module.Module
import quoi.module.settings.impl.ColourSetting
import quoi.module.settings.impl.NumberSetting
import quoi.utils.ui.hud.Hud
import quoi.utils.ui.hud.ScopedHud

class ResizableHud(
    name: String,
    module: Module,
    toggleable: Boolean = true,
    private val width: NumberSetting<Float>,
    private val height: NumberSetting<Float>,
    private val colour: ColourSetting,
    private val outline: ColourSetting?,
    private val thickness: NumberSetting<Float>?,
    content: Scope.() -> Unit
) : ScopedHud<ResizableHud.Scope>(name, module, toggleable, content) {

    class Scope(
        parent: Hud.Scope,
        val width: Pixel, val height: Pixel, val colour: Colour,
        val outline: Colour, val thickness: Pixel
    ) : Hud.Scope(parent.element, parent.preview)

    override fun createScope(base: Hud.Scope): Scope {
        return Scope(
            parent = base,
            width = width.value.px,
            height = height.value.px,
            colour = colour.value,
            outline = outline?.value ?: Colour.WHITE,
            thickness = thickness?.value?.px ?: 0.px
        )
    }


    fun resize(scope: ElementScope<*>, element: Element) = with(scope) {
        group(
            constrain(
                element.constraints.x,
                element.constraints.y,
                element.constraints.width,
                element.constraints.height
            )
        ) {
            var clickedX = 0f
            var clickedY = 0f
            var w = 0f
            var h = 0f

            var resizing = false

            onClick {
//                if (!CatKeyboard.Modifier.isShiftDown) return@onClick false
                clickedX = ui.mx
                clickedY = ui.my
                w = width.value
                h = height.value

                resizing = true
                true
            }

            onRelease {
                resizing = false
            }

            onMouseMove {
                if (!resizing) return@onMouseMove false
                println("TEST")
                val diffX = ui.mx - clickedX
                val diffY = ui.my - clickedY

                val newW = (w + diffX).coerceAtLeast(10f)
                val newH = (h + diffY).coerceAtLeast(10f)

                width.set(newW)
                height.set(newH)

                (element.constraints.width as? Pixel)?.pixels = newW
                (element.constraints.height as? Pixel)?.pixels = newH

                element.redraw()

                operation {
                    val tempScope = Scope(element, preview = true)
                    element.rebuild(tempScope)
                    true
                }

                true
            }
        }
    }
}