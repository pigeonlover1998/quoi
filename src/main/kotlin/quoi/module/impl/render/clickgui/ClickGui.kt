package quoi.module.impl.render.clickgui

import net.minecraft.util.Util
import quoi.annotations.AlwaysActive
import quoi.annotations.Internal
import quoi.api.abobaui.AbobaUI
import quoi.api.abobaui.constraints.impl.measurements.Animatable
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.constraints.impl.size.Fill
import quoi.api.abobaui.dsl.aboba
import quoi.api.abobaui.dsl.alignOpposite
import quoi.api.abobaui.dsl.alignRight
import quoi.api.abobaui.dsl.at
import quoi.api.abobaui.dsl.bounds
import quoi.api.abobaui.dsl.constrain
import quoi.api.abobaui.dsl.copies
import quoi.api.abobaui.dsl.draggable
import quoi.api.abobaui.dsl.minus
import quoi.api.abobaui.dsl.moveToBottom
import quoi.api.abobaui.dsl.onAdd
import quoi.api.abobaui.dsl.onClick
import quoi.api.abobaui.dsl.onMouseExit
import quoi.api.abobaui.dsl.onRemove
import quoi.api.abobaui.dsl.onScroll
import quoi.api.abobaui.dsl.percent
import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.dsl.radius
import quoi.api.abobaui.dsl.seconds
import quoi.api.abobaui.dsl.size
import quoi.api.abobaui.dsl.tonalHover
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
import quoi.module.ModuleManager
import quoi.module.impl.misc.Test
import quoi.module.impl.render.clickgui.impl.Data
import quoi.module.impl.render.clickgui.impl.Displays
import quoi.module.impl.render.clickgui.impl.PathSettings
import quoi.module.impl.render.clickgui.impl.PrefixSettings
import quoi.module.impl.render.clickgui.impl.VisualsSettings
import quoi.module.impl.render.clickgui.impl.VisualsSettings.moduleSorting
import quoi.module.settings.UIComponent
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.impl.SelectorComponent
import quoi.utils.StringUtils.capitaliseFirst
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.elements.themedInput
import quoi.utils.ui.hud.HudManager
import quoi.utils.ui.onHover
import quoi.utils.ui.rendering.NVGRenderer
import quoi.utils.ui.screens.UIScreen.Companion.open
import java.net.URI

@AlwaysActive
@OptIn(Internal::class)
object ClickGui : Module(
    "Click GUI",
    key = CatKeys.KEY_RIGHT_SHIFT
) {
    val forceSkyblock by switch("Force skyblock")
    val forceDungeons by switch("Force dungeon").onValueChanged { _, new -> if (new) Dungeon.setFloor(dungeonFloor.selected) }
    val dungeonFloor: SelectorComponent<Floor> by selector("Floor", Floor.F7).childOf(::forceDungeons).onValueChanged { _, new ->
        if (forceDungeons) Dungeon.setFloor(new.selected)
    }

    @Suppress("unused")
    private val sett = listOf(
        VisualsSettings,
        PrefixSettings,
        PathSettings,
        Displays,
        Data
    )

    override fun onKeybind() {
        open(clickGui)
    }

    private const val MODULE_SIZE = 30.0f

    var clickGui: AbobaUI.Instance = clickGui()
        private set

    private fun clickGui() = aboba("Quoi! Click Gui") {
        val moduleScopes = arrayListOf<Pair<Module, ElementScope<*>>>()
        ui.debug = Test.uiDebug
        onRemove {
            Config.save()
            HudManager.reinit(immediately = false)
        }

        for (category in Category.entries) {
            val data = Data.categoryData[category] ?: throw Exception("no good")

            column(at(x = data.x.px, y = data.y.px)) panel@{
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
                    "Discord" to { Util.getPlatform().openUri(URI("https://discord.com/invite/QCWgrQ57pN")) },
                    "Fork" to { Util.getPlatform().openUri(URI("https://github.com/jcnlk/quoi")) }
                ).forEach { (text, block) ->
                    block(
                        size(w = 105.px, h = 40.px),
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
                    Tag.FORK -> theme.forkImage
//                    else -> theme.chevronImage
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

            val lines = NVGRenderer.wrapText(desc, 200f, 14f, NVGRenderer.defaultFont)

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
        ModuleManager.modules.filter { it.category == category }.sortedWith(moduleSorting.selected.comparator)

    fun reopen() {
        mc.setScreen(null)
        clickGui = clickGui()
        open(clickGui)
    }
}