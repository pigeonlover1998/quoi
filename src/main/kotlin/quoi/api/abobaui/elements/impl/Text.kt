package quoi.api.abobaui.elements.impl

import quoi.QuoiMod.mc
import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.Positions
import quoi.api.abobaui.dsl.at
import quoi.api.abobaui.dsl.percent
import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.dsl.withScale
import quoi.api.abobaui.elements.AbobaDSL
import quoi.api.abobaui.elements.Element
import quoi.api.abobaui.elements.ElementScope
import quoi.api.colour.Colour
import quoi.api.colour.multiply
import quoi.utils.StringUtils.width
import quoi.utils.render.DrawContextUtils.drawString
import quoi.utils.ui.rendering.Font
import quoi.utils.ui.rendering.NVGRenderer
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor
import net.minecraft.util.FormattedCharSequence

open class Text(
    string: String,
    val font: Font,
    colour: Colour,
    constraints: Positions,
    size: Constraint.Size
) : Element(constraints, colour) {

    protected var maxWidth: Constraint.Size? = null

    init {
        constraints.height = size
    }

    open var text: String = string
        set(value) {
            if (field == value) return
            field = value
            redraw()
            previousHeight = 0f
        }

    protected var shadow: Boolean = false

    protected var previousHeight = 0f

    override fun prePosition() {

        if (maxWidth != null) {

            val limit = maxWidth!!.calculateSize(this, horizontal = true)
            val w = getTextWidth()

            if (w > limit && w > 0) {
                val ratio = limit / w
                val newH = height * ratio
                constraints.height = newH.px

                width = limit

                previousHeight = newH
            } else {
                if (constraints.width.undefined()) width = w
            }

            return
        }

        if (previousHeight != height) {
            previousHeight = height
            if (constraints.width.undefined()) width = getTextWidth()
        }
    }

    override fun draw() {
        drawText(text, colour = colour!!.rgb)
    }

    protected fun drawText(string: String, x: Float = this.x, y: Float = this.y, colour: Int) {
        if (font.name == "Minecraft") {
            val fontScale = height / mc.font.lineHeight
            withScale {
                if (shadow) {
                    val visual = Component.literal(string).visualOrderText

                    val shadowSeq = FormattedCharSequence { sink ->
                        visual.accept { index, style, codePoint ->
                            val base = style.color?.value ?: colour
                            val dark = TextColor.fromRgb(base.multiply(0.25f))
                            sink.accept(index, style.withColor(dark), codePoint)
                        }
                    }
                    ctx.drawString(shadowSeq, 2, 2, shadow = false, scale = fontScale)
                }
                ctx.drawString(string, 0, 0, colour, fontScale, false)
            }
            return
        }

        if (shadow) {
            val offset = height / 25f
            NVGRenderer.text(string, x + offset, y + offset, height, colour.multiply(0.25f), font)
        }
        NVGRenderer.text(string, x, y, height, colour, font)
    }

    open fun getTextWidth(): Float = textWidth(text)

    protected fun textWidth(string: String) =
        if (font.name == "Minecraft") text.width(height / mc.font.lineHeight) else NVGRenderer.textWidth(string, height, font)

    companion object {
        @AbobaDSL
        var <E : Text> ElementScope<E>.string
            get() = element.text
            set(value) { element.text = value }

        @AbobaDSL
        var <E : Text> ElementScope<E>.shadow
            get() = element.shadow
            set(value) { element.shadow = value }

        /**
         * Subclass of [Text], where text is supplied from a function.
         *
         * NOTE: It should only be used if text changes really often.
         */
        @AbobaDSL
        inline fun ElementScope<*>.textSupplied(
            crossinline supplier: () -> Any?,
            font: Font = NVGRenderer.defaultFont,
            colour: Colour = Colour.WHITE,
            pos: Positions = at(),
            size: Constraint.Size = 50.percent
        ): ElementScope<Text> = object : Text(supplier().toString(), font, colour, pos, size) {
            override fun draw() {
                text = supplier().toString()
                super.draw()
            }
        }.scope { /* no-op */ }

        @AbobaDSL
        fun ElementScope<Text>.maxWidth(size: Constraint.Size) {
            element.maxWidth = size
        }
    }
}