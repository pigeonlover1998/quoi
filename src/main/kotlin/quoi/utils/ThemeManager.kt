package quoi.utils

import quoi.api.abobaui.constraints.impl.measurements.Pixel
import quoi.api.abobaui.dsl.px
import quoi.api.colour.Colour
import quoi.api.colour.colour
import quoi.api.colour.toHSB
import quoi.module.impl.render.ClickGui.seedColour
import quoi.module.impl.render.ClickGui.selectedTheme
import quoi.utils.ui.rendering.NVGRenderer.image

// https://m3.material.io/styles/color
object ThemeManager {

    class TonalPalette(
        private val hueOverride: Double? = null,
        private val hueOffset: Double = 0.0,
        private val chromaOverride: Double? = null,
        private val chromaMultiplier: Double = 1.0
    ) {
        fun tone(t: Int): Colour {
            var lastSeed: Int = -1
            var cache: Int = -1

            return colour {
                val seed = seedColour.rgb

                if (seed != lastSeed) {
                    val cam = Cam16.fromInt(seed)
                    val h = hueOverride ?: ((cam.hue + hueOffset) % 360.0)
                    val c = chromaOverride ?: (cam.chroma * chromaMultiplier)

                    val solved = HctSolver.solveToInt(h, c, t.toDouble())
                    cache = (solved and 0x00FFFFFF) or (0xFF shl 24)
                    lastSeed = seed
                }

                cache
            }
        }
    }

    private val primaryPalette = TonalPalette()
    private val secondaryPalette = TonalPalette(chromaOverride = 16.0)
    private val tertiaryPalette = TonalPalette(hueOffset = 60.0, chromaOverride = 24.0)
    private val neutralPalette = TonalPalette(chromaOverride = 4.0)
    private val neutralVariantPalette = TonalPalette(chromaOverride = 8.0)
    private val errorPalette = TonalPalette(hueOverride = 25.0, chromaOverride = 84.0)

    val theme get() = when(selectedTheme.selected) {
        "Dark" -> DarkTheme
        "Light" -> LightTheme
        else -> OnyxTheme
    }

    val DarkTheme = Theme(
        name = "Dark",
        primary = primaryPalette.tone(80),
        onPrimary = primaryPalette.tone(20),
        primaryContainer = primaryPalette.tone(30),
        onPrimaryContainer = primaryPalette.tone(90),

        secondary = secondaryPalette.tone(80),
        onSecondary = secondaryPalette.tone(20),
        secondaryContainer = secondaryPalette.tone(30),
        onSecondaryContainer = secondaryPalette.tone(90),

        tertiary = tertiaryPalette.tone(80),
        onTertiary = tertiaryPalette.tone(20),
        tertiaryContainer = tertiaryPalette.tone(30),
        onTertiaryContainer = tertiaryPalette.tone(90),

        error = errorPalette.tone(80),
        onError = errorPalette.tone(20),
        errorContainer = errorPalette.tone(30),
        onErrorContainer = errorPalette.tone(90),

        background = neutralPalette.tone(6),
        onBackground = neutralPalette.tone(90),

        surface = neutralPalette.tone(6),
        surfaceContainerLow = neutralPalette.tone(10),
        surfaceContainer = neutralPalette.tone(12),
        surfaceContainerHigh = neutralPalette.tone(17),
        surfaceContainerHighest = neutralPalette.tone(22),

        onSurface = neutralPalette.tone(90),
        surfaceVariant = neutralVariantPalette.tone(30),
        onSurfaceVariant = neutralVariantPalette.tone(80),

        outline = neutralVariantPalette.tone(60),
        outlineVariant = neutralVariantPalette.tone(30)
    )

    val LightTheme = Theme(
        name = "Light",
        primary = primaryPalette.tone(40),
        onPrimary = primaryPalette.tone(100),
        primaryContainer = primaryPalette.tone(90),
        onPrimaryContainer = primaryPalette.tone(10),

        secondary = secondaryPalette.tone(40),
        onSecondary = secondaryPalette.tone(100),
        secondaryContainer = secondaryPalette.tone(90),
        onSecondaryContainer = secondaryPalette.tone(10),

        tertiary = tertiaryPalette.tone(40),
        onTertiary = tertiaryPalette.tone(100),
        tertiaryContainer = tertiaryPalette.tone(90),
        onTertiaryContainer = tertiaryPalette.tone(10),

        error = errorPalette.tone(40),
        onError = errorPalette.tone(100),
        errorContainer = errorPalette.tone(90),
        onErrorContainer = errorPalette.tone(10),

        background = neutralPalette.tone(98),
        onBackground = neutralPalette.tone(10),

        surface = neutralPalette.tone(98),
        surfaceContainerLow = neutralPalette.tone(96),
        surfaceContainer = neutralPalette.tone(94),
        surfaceContainerHigh = neutralPalette.tone(92),
        surfaceContainerHighest = neutralPalette.tone(90),

        onSurface = neutralPalette.tone(10),
        surfaceVariant = neutralVariantPalette.tone(90),
        onSurfaceVariant = neutralVariantPalette.tone(30),

        outline = neutralVariantPalette.tone(50),
        outlineVariant = neutralVariantPalette.tone(80)
    )

    val OnyxTheme = Theme(
        name = "Onyx",

        primary = primaryPalette.tone(80),
        onPrimary = primaryPalette.tone(20),
        primaryContainer = primaryPalette.tone(30),
        onPrimaryContainer = primaryPalette.tone(90),

        secondary = secondaryPalette.tone(80),
        onSecondary = secondaryPalette.tone(20),
        secondaryContainer = secondaryPalette.tone(30),
        onSecondaryContainer = secondaryPalette.tone(90),

        tertiary = tertiaryPalette.tone(80),
        onTertiary = tertiaryPalette.tone(20),
        tertiaryContainer = tertiaryPalette.tone(30),
        onTertiaryContainer = tertiaryPalette.tone(90),

        error = errorPalette.tone(80),
        onError = errorPalette.tone(20),
        errorContainer = errorPalette.tone(30),
        onErrorContainer = errorPalette.tone(90),

        background = neutralPalette.tone(0),
        onBackground = neutralPalette.tone(90),

        surface = neutralPalette.tone(0),
        surfaceContainerLow = neutralPalette.tone(2),
        surfaceContainer = neutralPalette.tone(4),
        surfaceContainerHigh = neutralPalette.tone(6),
        surfaceContainerHighest = neutralPalette.tone(10),

        onSurface = neutralPalette.tone(90),
        surfaceVariant = neutralVariantPalette.tone(30),
        onSurfaceVariant = neutralVariantPalette.tone(80),

        outline = neutralVariantPalette.tone(60),
        outlineVariant = neutralVariantPalette.tone(30)
    )

    data class Theme(
        val name: String,

        val primary: Colour,
        val onPrimary: Colour,
        val primaryContainer: Colour,
        val onPrimaryContainer: Colour,

        val secondary: Colour,
        val onSecondary: Colour,
        val secondaryContainer: Colour,
        val onSecondaryContainer: Colour,

        val tertiary: Colour,
        val onTertiary: Colour,
        val tertiaryContainer: Colour,
        val onTertiaryContainer: Colour,

        val error: Colour,
        val onError: Colour,
        val errorContainer: Colour,
        val onErrorContainer: Colour,

        val background: Colour,
        val onBackground: Colour,

        val surface: Colour,
        val surfaceContainerLow: Colour,
        val surfaceContainer: Colour,
        val surfaceContainerHigh: Colour,
        val surfaceContainerHighest: Colour,

        val onSurface: Colour,
        val surfaceVariant: Colour,
        val onSurfaceVariant: Colour,

        val outline: Colour,
        val outlineVariant: Colour,

        val textSize: Pixel = 16.px
    ) {
        @Deprecated("use material")
        val panel: Colour get() = surfaceContainerLow
        @Deprecated("use material")
        val border: Colour get() = outlineVariant

        val isDark get() = background.toHSB().brightness < 0.5f

        val chevronImage get() = "chevron.svg".image()
        val gearImage get() = "gear.svg".image()
        val moveImage get() = "move.svg".image()
        val pickerImage get() = "picker.svg".image()
        val refreshImage get() = "refresh.svg".image()
    }
}