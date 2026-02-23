package quoi.utils

import quoi.api.abobaui.constraints.impl.measurements.Pixel
import quoi.api.abobaui.dsl.px
import quoi.api.colour.Colour
import quoi.api.colour.colour
import quoi.api.colour.multiply
import quoi.api.colour.toHSB
import quoi.module.impl.render.ClickGui.accentColour
import quoi.module.impl.render.ClickGui.background
import quoi.module.impl.render.ClickGui.border
import quoi.module.impl.render.ClickGui.panel
import quoi.module.impl.render.ClickGui.selectedTheme
import quoi.module.impl.render.ClickGui.textPrimary
import quoi.module.impl.render.ClickGui.textSecondary
import quoi.utils.ui.rendering.NVGRenderer.image

object ThemeManager {

    val theme get() = when(selectedTheme.selected) {
//        "Light" -> LightTheme
        "Dark" -> DarkTheme
        "Custom" -> CustomTheme
        else -> LightTheme
    }

    val DarkTheme = Theme(
        name = "Dark",
        background = Colour.RGB(46, 46, 46),
        panel = Colour.RGB(58, 58, 58),
        card = Colour.RGB(68, 68, 68),
        textPrimary = Colour.RGB(255, 255, 255),
        textSecondary = Colour.RGB(187, 187, 187),
        border = Colour.RGB(80, 80, 80)
    )

    val LightTheme = Theme(
        name = "Light",
        background = Colour.RGB(245, 245, 245),
        panel = Colour.RGB(255, 255, 255),
        card = Colour.RGB(240, 240, 240),
        textPrimary = Colour.RGB(0, 0, 0),
        textSecondary = Colour.RGB(68, 68, 68),
        border = Colour.RGB(204, 204, 204)
    )

    val CustomTheme = Theme( // dynamic only so the user could see how the ui looks in real time. idk how good it is for performance
        name = "Custom",
        background = colour { background.rgb },
        panel = colour { panel.rgb },
        card = Colour.RGB(240, 240, 240),
        textPrimary = colour { textPrimary.rgb },
        textSecondary = colour { textSecondary.rgb },
        border = colour { border.rgb }
    )

    data class Theme(
        val name: String,
        val background: Colour,
        val panel: Colour,
        val card: Colour,
        val textPrimary: Colour,
        val textSecondary: Colour,
        val border: Colour,
        val textSize: Pixel = 16.px
    ) {
        val accent: Colour = colour { accentColour.rgb }
        var accentBrighter: Colour = colour { Colour.RGB(accent.rgb.multiply(1.15f)).rgb }
        val caretColour get() = if (theme.isDark) Colour.WHITE else Colour.BLACK
        val danger: Colour = Colour.RGB(231, 111, 81)

        val isDark get() = background.toHSB().brightness < 0.5f
        private val imgCol get() = if (theme.isDark) "white" else "black"

        val chevronImage get() = "chevron-$imgCol.svg".image()
        val gearImage get() = "gear-$imgCol.svg".image()
        val moveImage get() = "move-$imgCol.svg".image()
        val pickerImage get() = "picker-$imgCol.svg".image()
    }
}