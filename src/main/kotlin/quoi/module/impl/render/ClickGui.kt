@file:Suppress("UNUSED")

package quoi.module.impl.render

import net.minecraft.Util
import quoi.annotations.AlwaysActive
import quoi.api.ServerInfo.averagePing
import quoi.api.ServerInfo.averageTps
import quoi.api.ServerInfo.currentPing
import quoi.api.ServerInfo.currentTps
import quoi.api.ServerInfo.medianPing
import quoi.api.abobaui.AbobaUI
import quoi.api.abobaui.constraints.impl.measurements.Animatable
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.constraints.impl.size.Fill
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.Layout.Companion.divider
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.abobaui.elements.impl.Popup
import quoi.api.abobaui.elements.impl.Scrollable.Companion.scroll
import quoi.api.abobaui.elements.impl.TextInput.Companion.maxWidth
import quoi.api.abobaui.elements.impl.TextInput.Companion.onTextChanged
import quoi.api.abobaui.elements.impl.layout.Column
import quoi.api.abobaui.elements.impl.popup
import quoi.api.animations.Animation
import quoi.api.colour.Colour
import quoi.api.colour.colour
import quoi.api.colour.withAlpha
import quoi.api.input.CatKeys
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Floor
import quoi.config.Config
import quoi.module.Category
import quoi.module.Module
import quoi.module.ModuleManager.modules
import quoi.module.impl.misc.Test
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.impl.MapSetting
import quoi.module.settings.impl.SelectorComponent
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.capitaliseFirst
import quoi.utils.StringUtils.percentColour
import quoi.utils.StringUtils.toFixed
import quoi.utils.StringUtils.width
import quoi.utils.ThemeManager.theme
import quoi.utils.WorldUtils.day
import quoi.utils.ui.elements.themedInput
import quoi.utils.ui.hud.HudManager
import quoi.utils.ui.onHover
import quoi.utils.ui.rendering.NVGRenderer
import quoi.utils.ui.rendering.NVGRenderer.defaultFont
import quoi.utils.ui.screens.UIScreen.Companion.open
import quoi.utils.ui.textPair
import java.net.URI

@AlwaysActive
object ClickGui : Module(
    "Click GUI",
    key = CatKeys.KEY_RIGHT_SHIFT
) {
    val forceSkyblock by switch("Force skyblock")
    val forceDungeons by switch("Force dungeon").onValueChanged { old, new -> if (new) Dungeon.setFloor(dungeonFloor.selected) }
    val dungeonFloor: SelectorComponent<Floor> by selector("Floor", Floor.F7).childOf(::forceDungeons).onValueChanged { old, new ->
        if (forceDungeons) Dungeon.setFloor(new.selected)
    }

    val selectedTheme by selector("Theme", "Light", arrayListOf("Light", "Dark", "Onyx")).onValueChanged { _, _ ->
        reopen()
    }.open()

    val seedColour by colourPicker("Colour", Colour.RGB(255, 204, 134)).json("Theme seed").childOf(::selectedTheme).asParent()
    val moduleSorting by selector("Module sorting", ModuleSorting.Alphabetical).childOf(::selectedTheme).onValueChanged { _, _ -> reopen() }

    var rainbowSpeed by slider("Rainbow colour speed", 1.0f, 0.05f, 5.0f, 0.05f)
    
    private val prefixDropdown by text("Prefix settings")
    val prefixText by textInput("Prefix", "quoi!").childOf(::prefixDropdown)
    val prefixColour by colourPicker("Colour", Colour.GREEN).json("Prefix colour").childOf(::prefixDropdown)
    val bracketsColour by colourPicker("Brackets", Colour.WHITE).json("Brackets colour").childOf(::prefixDropdown)

    private val fpsHud by textHud("Fps display") {
        textPair(
            string = "Fps:",
            supplier = { mc.fps },
            labelColour = colour,
            shadow = shadow,
            font = font
        )
    }.setting()

    private val pingType by selector("Ping type", PingType.Average)
    private val pingHud by textHud("Ping display") {
        visibleIf { !mc.isSingleplayer }
        textPair(
            string = "Ping:",
            supplier = { (if (preview) 69.420 else pingType.selected.value()).formatPing },
            labelColour = colour,
            shadow = shadow,
            font = font
        )
    }.withSettings(::pingType).setting()

    private val tpsType by selector("Tps type", TpsType.Average)
    private val tpsHud by textHud("Tps display") {
        visibleIf { !mc.isSingleplayer }
        textPair(
            string = "Tps:",
            supplier = { if (preview) 17.56f.formatTps(2) else tpsType.selected.value().formatTps(2) },
            labelColour = colour,
            shadow = shadow,
            font = font
        )
    }.withSettings(::tpsType).setting()

    private val dayHud by textHud("Day display") {
        textPair(
            string = "Day:",
            supplier = { mc.level?.day },
            labelColour = colour,
            shadow = shadow,
            font = font
        )
    }.setting()

    private val categoryData by MapSetting("category data", mutableMapOf<Category, CategoryData>()).also { setting ->
        Category.entries.forEach {
            setting.value[it] = CategoryData(x = 10f + 265f * it.ordinal, y = 10f, extended = true)
        }
    }
    
    private var currentPet by textInput("Current pet", "").hide() // just for cfg

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

    private const val MODULE_SIZE = 30.0f

    var clickGui: AbobaUI.Instance = clickGui()
        private set

    private fun clickGui() = aboba("Quoi! Click Gui") { // todo redesign
        val moduleScopes = arrayListOf<Pair<Module, ElementScope<*>>>()
        ui.debug = Test.uiDebug
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
                    radius = 6.radius()
                )
                onRemove {
                    data.x = element.x
                    data.y = element.y
                }
                val height = Animatable(from = Bounding, to = 0.px, swapIf = !data.extended)
                block(
                    size(260.px, (MODULE_SIZE + 5).px),
                    colour = theme.surface,
                    radius(tl = 6, tr = 6)
                ) {
                    text(
                        string = category.name.capitaliseFirst(),
                        size = 70.percent,
                        colour = theme.onSurface
                    )

                    onClick(button = 1) {
                        height.animate(0.25.seconds, style = Animation.Style.EaseInOutQuint)
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
                            colour = colour { theme.surface.withAlpha(0.7f).rgb }
                        )
                        for (module in modulesFor(category)) {
                            moduleScopes.add(module to module(module))
                        }
                    }
                }

                block(
                    size(260.px, 10.px),
                    colour = theme.surface,
                    radius(bl = 6, br = 6)
                )

                onScroll { (amount) ->
                    scrollable.scroll(amount * -(MODULE_SIZE * 2f))
                }
            }
        }

        column(
            constrain(
                x = Centre, y = 90.percent,
                w = 375.px, h = Bounding
            ),
            gap = 10.px
        ) {
            draggable(button = 1)

            row(at(x = Centre), gap = 10.px) {

                mapOf(
                    "Hud editor" to { open(HudManager.editor(fromMain = true)) },
                    "Discord" to { Util.getPlatform().openUri(URI("https://discord.com/invite/QCWgrQ57pN")) }
                ).forEach { (text, block) ->
                    block(
                        size(w = 135.px, h = 40.px),
                        colour = theme.surface,
                        radius = 10.radius()
                    ) {
                        outline(theme.primary, thickness = 2.px)
                        tonalHover()
                        text(
                            string = text,
                            size = theme.textSize,
                            colour = theme.onSurface
                        )
                        onClick {
                            block()
                        }
                    }
                }
            }

            themedInput(
                size = size(Copying, 40.px),
                colour = theme.surface,
                radius = 10.radius()
            ) {
                textInput(
                    placeholder = "Search...",
                    colour = theme.onSurface,
                    placeHolderColour = theme.onSurfaceVariant,
                    caretColour = theme.primary,
                ) {
                    maxWidth(Fill - 3.percent)
                    onTextChanged { (string) ->
                        moduleScopes.forEach { (m, element) ->
                            element.enabled =
                                m.name.contains(string, true) ||
                                        m.desc.contains(string, true) ||
                                        m.settings.any { it.name.contains(string, true) }
                        }
                    }
                }
            }
        }.element.moveToBottom()
    }

    private fun ElementScope<*>.module(module: Module) = column(size(Copying)) {
        var loaded = false
        lateinit var settings: ElementScope<Column>

        val col = Colour.Animated(
            from = theme.surface,
            to = theme.primaryContainer,
            swapIf = module.enabled
        )
        val height = Animatable(from = 0.px, to = Bounding)

        block(
            size(Copying, MODULE_SIZE.px),
            colour = col,
        ) {
//            hoverEffect(factor = 1.15f)
            tonalHover()
            description(module.desc)
            text(
                string = module.name,
                size = 18.px,
                colour = theme.onSurface
            )

            if (module.tag != Tag.NONE) {
                val img = when (module.tag) {
                    Tag.LEGACY -> theme.refreshImage
                    Tag.BETA   -> theme.bugImage
                    else -> theme.chevronImage
                }

                image(
                    image = img,
                    constraints = constrain(3.percent.alignOpposite, w = 20.px, h = 20.px),
                    colour = theme.onSurfaceVariant
                ).description(module.tag.desc)
            }

            var lastEnabled = module.enabled
            onAdd {
                if (lastEnabled != module.enabled) {
                    lastEnabled = module.enabled
                    col.swap()
                    redraw()
                }
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
                        module.settings.forEach { setting ->
                            if (setting !is UIComponent || setting.parent != null) return@forEach
                            setting.render(this)
                        }
                        divider(0.px)
                    }
                    loaded = true
                }
                height.animate(0.25.seconds, style = Animation.Style.EaseInOutQuint)
                element.redraw()
                true
            }
        }
        settings = column(constrain(x = 7.px, w = Copying - 14.px, h = height), gap = 9.px) {
            divider(9.px)
        }
    }

    fun ElementScope<*>.description(desc: String) {
        if (desc.isEmpty()) return

        var popup: Popup? = null

        onHover(duration = 0.5.seconds) {
            if (popup != null) return@onHover

            val x =
                    if (element.x >= ui.main.width / 2)
                        (element.x - 8).px.alignRight
                    else
                        (element.x + element.width + 8).px

            val y = (element.y + 7 / 2).px

            val lines = NVGRenderer.wrapText(desc, 200f, 14f, defaultFont)

            popup = popup(constrain(x, y, Bounding, Bounding), smooth = false) {
                block(
                    constraints = bounds(padding = 5.px),
                    colour = theme.surfaceContainerHighest,
                    5.radius()
                ) {
                    outline(theme.outline, thickness = 2.px)
                    column {
                        lines.forEach {
                            text(
                                string = it,
                                size = theme.textSize - 2.px,
                                colour = theme.onSurface
                            )
                        }
                    }
                }
            }
        }

        onMouseExit {
            popup?.closePopup()
            popup = null
        }
    }

    private fun modulesFor(category: Category): List<Module> =
        modules.filter { it.category == category }.sortedWith(moduleSorting.selected.comparator)
    
    fun currentPet() = currentPet
    fun updateCurrentPet(str: String) {
        currentPet = str
    }

    private val Double.formatPing get() = "§${ // fixme
        when {
            this < 50.0 -> "a"// Colour.MINECRAFT_GREEN
            this < 100.0 -> "2"// Colour.MINECRAFT_DARK_GREEN
            this < 150.0 -> "e"// Colour.MINECRAFT_YELLOW
            this < 200.0 -> "6"// Colour.MINECRAFT_GOLD
            else -> "c"// Colour.MINECRAFT_RED
        }
    }%.2f §7ms".format(this)

    private fun Float.formatTps(decimals: Int = 0) = (this - 15).percentColour(5.0) + this.toFixed(decimals) // fixme

    fun reopen() {
        mc.setScreen(null)
        clickGui = clickGui()
        open(clickGui)
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

    enum class ModuleSorting(
        val comparator: Comparator<Module>
    ) {
        WidthDescending(
            compareByDescending<Module> { NVGRenderer.textWidth(it.name, 18f, defaultFont) }.thenBy { it.name.lowercase() }
        ),

        WidthAscending(
            compareBy<Module> { NVGRenderer.textWidth(it.name, 18f, defaultFont) }.thenBy { it.name.lowercase() }
        ),

        Alphabetical(
            compareBy<Module> { it.name.lowercase() }
        );
    }
}