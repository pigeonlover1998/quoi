package quoi.module.impl.misc

import quoi.api.abobaui.dsl.*
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
import quoi.api.events.core.EventPriority
import quoi.api.input.CursorShape
import quoi.module.Module
import quoi.module.settings.impl.ColourSetting
import quoi.utils.StringUtils.toFixed
import quoi.utils.render.DrawContextUtils.rect
import quoi.utils.skyblock.ItemUtils.loreString
import quoi.utils.ui.cursor
import quoi.utils.ui.delegateClick
import quoi.utils.ui.hud.TextHud
import quoi.utils.ui.hud.setting
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import kotlin.math.pow

object Inventory : Module(
    "Inventory",
    desc = "Various quality of life features for inventory GUIs"
) {

    private val bgColour by ColourSetting("Background colour", Colour.GREY.withAlpha(100), allowAlpha = true)
    private val outlineColour by ColourSetting("Outline colour", Colour.GREY.withAlpha(150), allowAlpha = true)
    private val nameColour by ColourSetting("Name match", Colour.WHITE.withAlpha(200), allowAlpha = true)
    private val loreColour by ColourSetting("Lore colour", Colour.MAGENTA.withAlpha(200), allowAlpha = true)

    private val searchBar by TextHud("Search bar") {
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
                cursor(CursorShape.IBEAM)

                shadow = this@TextHud.shadow

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

            delegateClick(input)
        }
    }.container().withSettings(::bgColour, ::outlineColour, ::nameColour, ::loreColour).setting()

    private var searchText = ""
    private var focused = false
    private var highlightSlots = mutableListOf<HighlightSlot>()

    val equationMap: Map<String, (Double, Double) -> Double> = mapOf(
        "x" to Double::times,
        "*" to Double::times,
        "/" to Double::div,
        "+" to Double::plus,
        "-" to Double::minus,
        "%" to Double::rem,
        "^" to Double::pow
    )

    init {
        on<TickEvent.End> {
            highlightSlots.clear()
            if (mc.screen !is AbstractContainerScreen<*> || !searchBar.enabled || searchText.isEmpty()) return@on

            val queries = searchText.lowercase().split(",").map { it.trim() }
            player.containerMenu.items.forEachIndexed { i, stack ->
                val name = stack.customName?.string?.lowercase()?.trim().orEmpty()
                val lore = stack.loreString?.lowercase()?.trim().orEmpty()
                if (name.isEmpty() && lore.isEmpty()) return@forEachIndexed
                queries.forEach {
                    matchType(name, lore, it)?.let { lore ->
                        highlightSlots.add(HighlightSlot(i, if (lore) loreColour else nameColour))
                    }
                }
            }
        }

        on<GuiEvent.Slot.Draw> {
            val colour = highlightSlots.find { it.slot == slot.index }?.colour?.rgb ?: return@on
            ctx.rect(slot.x, slot.y, 16, 16, colour)
        }

        on<GuiEvent.Key> (EventPriority.LOW) {
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
            return calculate(s.replaceRange(it.range, calculate(it.groupValues[1]).toString()))
        }


        listOf("\\^", "[*x/%]", "[+\\-]").forEach { operators ->
            val eqRegex = Regex("""([\d.]+)\s*($operators)\s*([\d.]+)""")

            var match = eqRegex.find(s)
            while (match != null) {
                val (n1, op, n2) = match.destructured
                val result = equationMap.getValue(op)(n1.toDouble(), n2.toDouble())
                s = s.replaceRange(match.range, result.toString())
                match = eqRegex.find(s)
            }
        }

        return s.toDoubleOrNull()
    }


    data class HighlightSlot(var slot: Int, val colour: Colour)
}