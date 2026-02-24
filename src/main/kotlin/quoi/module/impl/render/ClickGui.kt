package quoi.module.impl.render

import quoi.api.ServerInfo.averagePing
import quoi.api.ServerInfo.averageTps
import quoi.api.ServerInfo.currentPing
import quoi.api.ServerInfo.currentTps
import quoi.api.ServerInfo.medianPing
import quoi.api.abobaui.constraints.impl.measurements.Animatable
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.Layout.Companion.divider
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.abobaui.elements.impl.Popup
import quoi.api.abobaui.elements.impl.Scrollable.Companion.scroll
import quoi.api.abobaui.elements.impl.TextInput.Companion.onTextChanged
import quoi.api.abobaui.elements.impl.layout.Column
import quoi.api.abobaui.elements.impl.popup
import quoi.api.animations.Animation
import quoi.api.colour.Colour
import quoi.api.colour.colour
import quoi.api.colour.withAlpha
import quoi.api.input.CatKeys
import quoi.config.Config
import quoi.module.Category
import quoi.module.Module
import quoi.module.ModuleManager.modules
import quoi.module.settings.AlwaysActive
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.UISetting
import quoi.module.settings.impl.*
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.percentColour
import quoi.utils.StringUtils.toFixed
import quoi.utils.ThemeManager.LightTheme
import quoi.utils.ThemeManager.theme
import quoi.utils.WorldUtils.day
import quoi.utils.ui.hud.HudManager
import quoi.utils.ui.hud.TextHud
import quoi.utils.ui.hud.setting
import quoi.utils.ui.onHover
import quoi.utils.ui.screens.UIScreen.Companion.open
import quoi.utils.ui.settingFromK0
import quoi.utils.ui.textPair

@AlwaysActive
object ClickGui : Module(
    "Click GUI",
    key = CatKeys.KEY_RIGHT_SHIFT
) {

    val forceDungeons by BooleanSetting("Force dungeons")
    val accentColour by ColourSetting("Colour", Colour.RGB(107, 203, 119))

    var rainbowSpeed by NumberSetting("Rainbow colour speed", 1.0f, 0.05f, 5.0f, 0.05f)

    val selectedTheme by SelectorSetting("Theme", "Light", arrayListOf("Light", "Dark", "Custom")).onValueChanged { _, _ ->
        reopen()
    }

    private inline val isCustom get() = selectedTheme.selected == "Custom"
    private val themeColours by DropdownSetting("Custom theme colours").collapsible().withDependency { isCustom }
    val background by ColourSetting("Background", LightTheme.background).withDependency(themeColours) { isCustom }
    val panel by ColourSetting("Panel", LightTheme.panel).withDependency(themeColours) { isCustom }
    val textPrimary by ColourSetting("Primary text", LightTheme.textPrimary).withDependency(themeColours) { isCustom }
    val textSecondary by ColourSetting("Secondary text", LightTheme.textSecondary).withDependency(themeColours) { isCustom }
    val border by ColourSetting("Border", LightTheme.border).withDependency(themeColours) { isCustom }
    private val reset by ActionSetting("Reset") {
        setOf(::background, ::panel, ::textPrimary, ::textSecondary, ::border).forEach { property ->
            settingFromK0(property).reset()
        }
    }.withDependency(themeColours) { isCustom }
    
    private val prefixDropdown by DropdownSetting("Prefix settings").collapsible()
    val prefixText by StringSetting("Prefix", "quoi!").withDependency(prefixDropdown)
    val prefixColour by ColourSetting("Prefix colour", Colour.PINK).withDependency(prefixDropdown)
    val bracketsColour by ColourSetting("Brackets colour", Colour.WHITE).withDependency(prefixDropdown)

    private val fpsHud by TextHud("Fps display") {
        textPair(
            string = "Fps:",
            supplier = { mc.fps },
            labelColour = colour,
            shadow = shadow
        )
    }.setting()

    private val pingType by SelectorSetting("Ping type", PingType.Average)
    private val pingHud by TextHud("Ping display") {
        visibleIf { !mc.isSingleplayer }
        textPair(
            string = "Ping:",
            supplier = { (if (preview) 69.420 else pingType.selected.value()).formatPing },
            labelColour = colour,
            shadow = shadow
        )
    }.withSettings(::pingType).setting()

    private val tpsType by SelectorSetting("Tps type", TpsType.Average)
    private val tpsHud by TextHud("Tps display") {
        visibleIf { !mc.isSingleplayer }
        textPair(
            string = "Tps:",
            supplier = { if (preview) 17.56f.formatTps(2) else tpsType.selected.value().formatTps(2) },
            labelColour = colour,
            shadow = shadow
        )
    }.withSettings(::tpsType).setting()

    private val dayHud by TextHud("Day display") {
        textPair(
            string = "Day:",
            supplier = { mc.level?.day },
            labelColour = colour,
            shadow = shadow
        )
    }.setting()

    val categoryData by MapSetting("category data", mutableMapOf<Category, CategoryData>()).also { setting ->
        Category.entries.forEach {
            setting.value[it] = CategoryData(x = 10f + 265f * it.ordinal, y = 10f, extended = true)
        }
    }

    override fun onKeybind() {
        open(clickGui)
    }

    init {
        command.sub("tps") {
            modMessage("Tps: ${currentTps.formatTps()}&r, Average: ${averageTps.formatTps(2)}")
        }.description("Shows tps.")

        command.sub("ping") {
            modMessage("Ping: ${currentPing.formatPing}&r, Average: ${averagePing.formatPing}")
        }.description("Shows ping.")
    }

    private val Double.formatPing get() = "§${
        when {
            this < 50.0 -> "a"// Colour.MINECRAFT_GREEN
            this < 100.0 -> "2"// Colour.MINECRAFT_DARK_GREEN
            this < 150.0 -> "e"// Colour.MINECRAFT_YELLOW
            this < 200.0 -> "6"// Colour.MINECRAFT_GOLD
            else -> "c"// Colour.MINECRAFT_RED
        }
    }%.2f §7ms".format(this)

    private fun Float.formatTps(decimals: Int = 0) = (this - 15).percentColour(5.0) + this.toFixed(decimals)


    val moduleSize = 30.0f

    var clickGui = clickGui()
        private set

    private fun reopen() {
        mc.setScreen(null)
        clickGui = clickGui()
        open(clickGui)
    }

    private fun clickGui() = aboba("Quoi! Click Gui") {
        val moduleScopes = arrayListOf<Pair<Module, ElementScope<*>>>()

        onRemove {
            Config.save()
            HudManager.reinit(immediately = false)
        }

        for (category in Category.entries) {
            val data = categoryData[category] ?: throw Exception("no good")

            column(at(x = data.x.px, y = data.y.px)) panel@ {
                dropShadow(
                    colour = Colour.BLACK.withAlpha(0.25f),
                    blur = 10f,
                    spread = 5f,
                    radius = 10.radius()
                )
                onRemove {
                    data.x = element.x
                    data.y = element.y
                }
                val height = Animatable(from = Bounding, to = 0.px, swapIf = !data.extended)
                block(
                    size(260.px, (moduleSize + 5).px),
                    colour = theme.background,
                    radius(tl = 10, tr = 10)
                ) {
                    text(
                        string = category.name,
                        size = 70.percent,
                        colour = theme.textPrimary
                    )

                    onClick(button = 1) {
                        height.animate(0.3.seconds, style = Animation.Style.EaseInOutQuint)
                        redraw()
                        data.extended = !data.extended
                        println(moduleScopes)
                        true
                    }

                    draggable(moves = this@panel.element)
                }

                val scrollable = scrollable(size(Copying, Bounding)) {
                    column(size(Copying, height)) {
                        block(
                            copies(),
                            colour = colour { theme.background.withAlpha(0.7f).rgb }
                        )
                        for (module in modules.sortedBy { it.name }) {
                            if (module.category != category) continue
                            moduleScopes.add(module to module(module))
                        }
                    }
                }

                block(
                    size(260.px, 10.px),
                    colour = theme.background,
                    radius(bl = 10, br = 10)
                )

                onScroll { (amount) ->
                    scrollable.scroll(amount * -moduleSize)
                }
            }
        }

        block(
            constrain(x = Centre, y = 90.percent, w = 400.px, h = 40.px),
            colour = theme.background,
            10.radius()
        ) {
            outline(theme.accent, thickness = 2.px)
            draggable(button = 1)

            val input = textInput(
                placeholder = "Search",
                colour = theme.textPrimary,
                placeHolderColour = theme.textSecondary,
                caretColour = theme.caretColour,
            ) {
                onTextChanged { (string) ->
                    moduleScopes.forEach { (m, element) ->
                        element.enabled = m.name.contains(string, true) ||
                                m.desc.contains(string, true) ||
                                m.settings.any { it.name.contains(string, true) }
                    }
                }
            }

            onClick {
                ui.focus(input.element)
            }
        }
    }

    private fun ElementScope<*>.module(module: Module) = column(size(Copying)) {
        var loaded = false
        lateinit var settings: ElementScope<Column>

        val col = Colour.Animated(
            from = theme.background,
            to = theme.accentBrighter,
            swapIf = module.enabled
        )
        val height = Animatable(from = 0.px, to = Bounding)

        block(
            size(Copying, moduleSize.px),
            colour = col,
        ) {
            hoverEffect(factor = 1.15f)
            description(module.desc)
            text(
                string = module.name,
                size = 18.px,
                colour = theme.textPrimary
            )

            var lastEnabled = module.enabled
            onAdd {
//            operation {
                if (lastEnabled != module.enabled) {
                    lastEnabled = module.enabled
                    col.swap()
                    redraw()
                }
                false
            }

            onClick(button = 0) {
                col.animate(0.15.seconds, Animation.Style.Linear)
                module.toggle()
                lastEnabled = module.enabled
                true
            }

            onClick(button = 1) {
                if (!loaded) {
                    settings.apply {
                        divider(7.px)
                        module.settings.forEach { setting ->
                            if (setting !is UISetting || setting.parent != null) return@forEach
                            setting.render(this).description(setting.description, xOff = 3, yOff = -4)

                            if (setting is DropdownSetting) {
                                setting.children.forEach { child ->
                                    child.render(this).description(child.description, xOff = 3, yOff = -2)
                                }
                            }
                        }
                        divider(0.px)
                    }
                    loaded = true
                }
                height.animate(0.3.seconds, style = Animation.Style.EaseInOutQuint)
                element.redraw()
                true
            }
        }
        settings = column(constrain(x = 5.px, w = Copying - 10.px, h = height), gap = 7.px) {
        }
    }

    fun ElementScope<*>.description(desc: String, xOff: Int = 0, yOff: Int = 0) {
        if (desc.isEmpty()) return

        var popup: Popup? = null

        onHover(duration = 1.seconds) {
            if (popup != null) return@onHover

            val x = (element.x + element.width + 5 + xOff).px
            val y = (element.y + 3 + yOff).px

            popup = popup(constrain(x, y, Bounding, Bounding), smooth = false) {
                block(
                    constraints = bounds(padding = 5.px),
                    colour = theme.background,
                    5.radius()
                ) {
                    outline(theme.accent, thickness = 2.px)
                    text(
                        string = desc, // maybe should wrap
                        size = theme.textSize - 2.px,
                        colour = theme.textPrimary
                    )
                }
            }
        }

        onMouseExit {
            popup?.closePopup()
            popup = null
        }
    }

    data class CategoryData(var x: Float, var y: Float, var extended: Boolean) {
        val defaultX = x
        val defaultY = y
    }

    private enum class PingType(val value: () -> Double) {
        Average({ averagePing }),
        Current({ currentPing }),
        Median({ medianPing })
    }

    private enum class TpsType(val value: () -> Float) {
        Average({ averageTps }),
        Current({ currentTps })
    }
}