package quoi.module.impl.dungeon

import quoi.api.abobaui.constraints.impl.size.Fill
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.Layout.Companion.divider
import quoi.api.abobaui.elements.Layout.Companion.section
import quoi.api.abobaui.elements.impl.Text.Companion.maxWidth
import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.abobaui.elements.impl.layout.Column.Companion.sectionRow
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.GuiEvent
import quoi.api.input.CatKeys
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon.leapTeammates
import quoi.api.skyblock.dungeon.DungeonClass
import quoi.api.skyblock.dungeon.DungeonPlayer
import quoi.config.Config
import quoi.module.Module
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.*
import quoi.utils.ChatUtils.literal
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.equalsOneOf
import quoi.utils.skyblock.PartyUtils.membersNoSelf
import quoi.utils.skyblock.player.PlayerUtils.clickSlot
import quoi.utils.ui.rendering.NVGRenderer.minecraftFont
import quoi.utils.ui.screens.UIContainer
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation

object LeapMenu : Module(
    "Leap Menu",
    area = Island.Dungeon,
    desc = "Adds a custom leap menu."
) {
    val sorting by SelectorSetting("Sorting", "Class", arrayListOf("Class", "Name", "Custom", "No sorting"))
    val fillEmpty by BooleanSetting("Fill empty slots", desc = "Fills empty slots with remaining teammates if possible.").withDependency { sorting.selected == "Custom" }
    val customOrder by ListSetting("Custom sorting", mutableListOf<String>())
    private val onlyClass by BooleanSetting("Only class", desc = "Renders only classes.")
//    private val renderHeads by BooleanSetting("Render heads", desc = "Renders teammate heads.")
    private val bgCol by ColourSetting("Background colour", Colour.MINECRAFT_DARK_GRAY.withAlpha(150), allowAlpha = true, desc = "Leap menu background colour.")
    private val scale by NumberSetting("Scale", 5.0f, 0.1f, 15.0f, 0.1f, desc = "Leap menu scale.")
    private val gap by NumberSetting("Gap", 50, 0, 500, 10)
    private val topLeft by KeybindSetting("Top left", CatKeys.KEY_1, desc = "Leaps to the first person in the menu.").excluding(CatKeys.KEY_E) // maybe should get inventory open keybind. but idk
    private val topRight by KeybindSetting("Top right", CatKeys.KEY_2, desc = "Leaps to the second person in the menu.").excluding(CatKeys.KEY_E)
    private val botLeft by KeybindSetting("Bottom left", CatKeys.KEY_3, desc = "Leaps to the third person in the menu.").excluding(CatKeys.KEY_E)
    private val botRight by KeybindSetting("Bottom right", CatKeys.KEY_4, desc = "Leaps to the fourth person in the menu.").excluding(CatKeys.KEY_E)

    init {

        command.sub("order") { p1: String?, p2: String?, p3: String?, p4: String? ->
            val players = listOf(p1, p2, p3, p4).map { it?.lowercase() ?: "_" }
            if (players.all { it == "_" }) {
                modMessage(
                    "Leap order: ${customOrder.joinToString(", ")}",
                    chatStyle = Style.EMPTY.withClickEvent(ClickEvent.CopyToClipboard(customOrder.joinToString(" ")))
                )
                return@sub
            }
            customOrder.clear()
            customOrder.addAll(players)
            Config.save()
            modMessage(
                "Leap order set to: ${customOrder.joinToString(", ")}",
                chatStyle = Style.EMPTY.withClickEvent(ClickEvent.CopyToClipboard(customOrder.joinToString(" ")))
            )
        }.description("Sets custom leap order.")
        .suggests("p1") { membersNoSelf }
        .suggests("p2") { membersNoSelf }
        .suggests("p3") { membersNoSelf }
        .suggests("p4") { membersNoSelf }

        on<GuiEvent.Open.Post> {
            val chest = (screen as? AbstractContainerScreen<*>) ?: return@on
            if (chest.title?.string?.equalsOneOf("Spirit Leap", "Teleport to Player") == false) return@on
            UIContainer(menu(leapTeammates, chest)).open()
        }
    }

    private fun menu(teammates: List<DungeonPlayer>, containerScreen: AbstractContainerScreen<*>) = aboba {
        val spacing = (gap / 2).px
        val w = (128 * scale).px
        val h = (64 * scale).px

        grid(copies()) {
            teammates.forEachIndexed { i, teammate ->
                group(size(50.percent, 50.percent)) {
                    val isLeft = i % 2 == 0
                    val isTop = i < 2

                    val xPos = if (isLeft) spacing.alignOpposite else spacing
                    val yPos = if (isTop) spacing.alignOpposite else spacing

                    if (teammate != DungeonPlayer.EMPTY) {
                        block(
                            constrain(xPos, yPos, w, h),
                            colour = bgCol
                        ) {
                            column(constrain(x = 2.percent, w = Fill, h = Fill), gap = 5.percent) {
                                divider(10.percent)
                                if (!onlyClass) section(30.percent) {
                                    text(
                                        font = minecraftFont,
                                        string = teammate.name,
                                        size = 100.percent,
                                        colour = teammate.colour,
                                        pos = at(x = 0.px)
                                    ) {
                                        shadow = true
                                        maxWidth(this@column.element.constraints.width - 2.percent)
                                    }
                                }

                                sectionRow(25.percent, gap = 2.percent) {
                                    text(
                                        font = minecraftFont,
                                        string = teammate.clazz.name,
                                        size = 100.percent,
                                        colour = teammate.clazz.colour,
                                    ).shadow = true

                                    textSupplied(
                                        font = minecraftFont,
                                        supplier = { if (teammate.isDead) "(dead)" else "" },
                                        size = 75.percent,
                                        colour = Colour.MINECRAFT_RED,
                                        pos = at(y = 0.px.alignOpposite)
                                    )
                                }
                            }
                        }

                        onClick(nonSpecific = true) {
                            if (teammate.isDead) modMessage("this player is ded")
                            leapTo(teammate.name, teammate.colour, containerScreen)
                        }
                    }
                }
            }
        }

        onKeyPressed { (key) ->
            val index = listOf(topLeft, topRight, botLeft, botRight).indexOfFirst { it.key == key }.takeIf { it != -1 } ?: return@onKeyPressed false
            if (index >= leapTeammates.size) return@onKeyPressed false
            val player = leapTeammates[index]
            if (player.isDead) {
                modMessage("this player is ded")
                return@onKeyPressed false
            }
            leapTo(player.name, player.colour, containerScreen)
            true
        }
    }

    private fun leapTo(name: String, colour: Colour, screenHandler: AbstractContainerScreen<*>) {
        val slot = screenHandler.menu.slots.subList(11, 16).firstOrNull {
            it.item?.hoverName?.string?.substringAfter(' ').equals(name.noControlCodes, ignoreCase = true)
        }?.index ?: return
        mc.player?.clickSlot(slot, screenHandler.menu.containerId)
        modMessage(literal("Leaping to ").append(literal(name).withColor(colour.rgb)).append(literal(".")))
    }
}