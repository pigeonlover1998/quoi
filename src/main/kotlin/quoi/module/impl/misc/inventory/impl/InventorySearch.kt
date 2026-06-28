package quoi.module.impl.misc.inventory.impl

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import quoi.api.abobaui.dsl.alignOpposite
import quoi.api.abobaui.dsl.at
import quoi.api.abobaui.dsl.constrain
import quoi.api.abobaui.dsl.focused
import quoi.api.abobaui.dsl.minus
import quoi.api.abobaui.dsl.onFocusChanged
import quoi.api.abobaui.dsl.percent
import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.dsl.radius
import quoi.api.abobaui.dsl.toggle
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.string
import quoi.api.abobaui.elements.impl.TextInput.Companion.maxWidth
import quoi.api.abobaui.elements.impl.TextInput.Companion.onTextChanged
import quoi.api.colour.Colour
import quoi.api.colour.colour
import quoi.api.colour.multiply
import quoi.api.colour.toHSB
import quoi.api.colour.withAlpha
import quoi.api.events.GuiEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.api.input.CursorShape
import quoi.module.impl.misc.inventory.Inventory
import quoi.module.settings.group.SettingGroup
import quoi.module.settings.impl.HudComponent
import quoi.utils.StringUtils.toFixed
import quoi.utils.render.DrawContextUtils.rect
import quoi.utils.skyblock.item.ItemUtils.loreString
import quoi.utils.ui.cursor
import quoi.utils.ui.delegateClick
import quoi.utils.ui.hud.Hud
import quoi.utils.ui.inHudEditor
import kotlin.math.pow

object InventorySearch : SettingGroup(Inventory, HudComponent("Search Bar", Hud("Search Bar", Inventory, false))) {
    private val bgColour by colourPicker("Background colour", Colour.GREY.withAlpha(100), allowAlpha = true)
    private val outlineColour by colourPicker("Outline colour", Colour.GREY.withAlpha(150), allowAlpha = true)
    private val nameColour by colourPicker("Name match", Colour.WHITE.withAlpha(200), allowAlpha = true)
    private val loreColour by colourPicker("Lore colour", Colour.MAGENTA.withAlpha(200), allowAlpha = true)

    private var searchText = ""
    private var focused = false
    private val highlightSlots = mutableMapOf<Int, Colour>()

    private val searchBar = textHud("Search bar", font = null, anchor = null) {
        block(
            constrain(w = 360.px, h = 40.px),
            colour = bgColour,
            10.radius()
        ) {
            outline(outlineColour, thickness = 2.px)

            val calcText = text(
                string = "",
                pos = at(x = 3.percent.alignOpposite),
                colour = colour
            ).toggle()

            val input = textInput(
                string = searchText,
                placeholder = "Search",
                colour = colour,
                placeHolderColour = colour { colour.rgb.multiply(0.8f) },
                caretColour = if (bgColour.toHSB().brightness < 0.6f) Colour.WHITE else Colour.BLACK,
                pos = at(x = 3.percent)
            ) {

                shadow = this@textHud.shadow

                onTextChanged { (string) ->
                    searchText = string

                    calculate(string)?.let { result ->
                        val str = result.toFixed(4).trimEnd('0').trimEnd('.')
                        calcText.string = "= $str"
                        maxWidth(93.percent - calcText.element.getTextWidth().px)
                        calcText.enabled = true
                    } ?: run {
                        calcText.enabled = false
                        maxWidth(93.percent)
                    }
                }

                onFocusChanged {
                    focused = !focused()
                }
            }

            if (!inHudEditor) {
                cursor(CursorShape.IBEAM)
                delegateClick(input)
            }
        }
    }.container().withSettings(::bgColour, ::outlineColour, ::nameColour, ::loreColour)

    override val running: Boolean
        get() = super.running && searchBar.enabled

    init {
        @Suppress("unchecked_cast")
        (parent as HudComponent<Hud>).hud = searchBar

        on<TickEvent.End> {
            if (mc.screen !is AbstractContainerScreen<*>) return@on
            if (searchText.isEmpty()) {
                if (highlightSlots.isNotEmpty()) highlightSlots.clear()
                return@on
            }

            highlightSlots.clear()

            val queries = searchText.lowercase().split(",").map { it.trim() }
            player.containerMenu.items.forEachIndexed { i, stack ->
                val name = stack.customName?.string?.lowercase()?.trim().orEmpty()
                val lore = stack.loreString?.lowercase()?.trim().orEmpty()
                if (name.isEmpty() && lore.isEmpty()) return@forEachIndexed
                queries.forEach { query ->
                    matchType(name, lore, query)?.let { lore ->
                        highlightSlots[i] = if (lore) loreColour else nameColour
                    }
                }
            }
        }

        on<GuiEvent.Slot.Draw> {
            val colour = highlightSlots[slot.index]?.rgb ?: return@on
            ctx.rect(slot.x, slot.y, 16, 16, colour)
        }

        on<GuiEvent.Key.Press> (Priority.LOW) {
            if (focused) cancel()
        }
    }

    private fun matchType(name: String, lore: String, string: String) = when {
        name.isEmpty() || lore.isEmpty() || string.isEmpty() -> null
        name.contains(string, true) -> false
        lore.contains(string, true) -> true
        else -> null
    }

    private fun calculate(string: String): Double? {
        var s = string.replace(",", "")

        Regex("""\(([^()]+)\)""").find(s)?.let {
            val inner = calculate(it.groupValues[1]) ?: return null
            return calculate(s.replaceRange(it.range, inner.toString()))
        }

        listOf("\\^", "[*x/%]", "[+\\-]").forEach { operators ->
            val eqRegex = Regex("""(?<!\d)([+-]?[\d.]+)\s*($operators)\s*([+-]?[\d.]+)""")

            var match = eqRegex.find(s)
            while (match != null) {
                val (n1, op, n2) = match.destructured

                val num1 = n1.toDoubleOrNull() ?: return null
                val num2 = n2.toDoubleOrNull() ?: return null

                val result = equationMap.getValue(op)(num1, num2).toString()
                s = s.replaceRange(match.range, result)
                match = eqRegex.find(s)
            }
        }

        return s.toDoubleOrNull()
    }

    private val equationMap: Map<String, (Double, Double) -> Double> = mapOf(
        "x" to Double::times,
        "*" to Double::times,
        "/" to Double::div,
        "+" to Double::plus,
        "-" to Double::minus,
        "%" to Double::rem,
        "^" to Double::pow
    )
}