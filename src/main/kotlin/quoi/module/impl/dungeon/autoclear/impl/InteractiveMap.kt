package quoi.module.impl.dungeon.autoclear.impl

import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket
import quoi.api.abobaui.dsl.aboba
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.PacketEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.MapRenderer
import quoi.api.skyblock.dungeon.odonscanning.MapRenderer.renderMap
import quoi.api.skyblock.invoke
import quoi.module.Module
import quoi.module.impl.dungeon.autoclear.getLockedDoor
import quoi.module.impl.dungeon.autoclear.pathToDoor
import quoi.module.impl.dungeon.autoclear.pathToRoom
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.ChatUtils.modMessage
import quoi.utils.ui.hud.impl.TextHud
import quoi.utils.ui.screens.UIScreen.Companion.open

/**
 * TODO:
 *  room queuing while auto routing
 */
object InteractiveMap : Module(
    "Interactive Map",
    desc = "Automatically teleports to a specified target.",
    area = Island.Dungeon(inClear = true),
    tag = Tag.BETA
) {
    private val keepChunks by switch("Keep chunks loaded", true, desc = "Keeps the chunks loaded. Good for long distances.")
    private val closeOn by segmented("Close on", "Release", listOf("Release", "Repress"))

    @Suppress("unused")
    private val openKey by keybind("Open key")
        .onPress {
            if (!enabled || !Dungeon.inClear || Dungeon.isDead) return@onPress
            if (mapOpen && closeOn.index == 1) {
                mc.setScreen(null)
            } else if (mc.screen == null) {
                open(map(), background = false)
            }
        }
        .onRelease {
            if (!enabled || closeOn.index != 0 || !Dungeon.inClear || Dungeon.isDead) return@onRelease
            if (mapOpen) mc.setScreen(null)
        }

    @Suppress("unused")
    private val startKey by keybind("Start key", desc = "Teleports to start node in current room")
        .onPress {
            if (!enabled || !mapOpen || !Dungeon.inClear || Dungeon.isDead) return@onPress
            val room = Dungeon.currentRoom
                ?: return@onPress modMessage("Current room is null. This should never happen")
            pathToRoom(room, room.tiles.first(), 1)
        }

    @Suppress("unused")
    private val lockedKey by keybind("Locked door key", desc = "Teleports to the closest locked door")
        .onPress {
            if (!enabled || !mapOpen || !Dungeon.inClear || Dungeon.isDead) return@onPress
            val door = getLockedDoor() ?: return@onPress modMessage("No locked doors found.")
            pathToDoor(door)
        }

    private val visuals by text("Visuals")
    private val shadow by switch("Shadow", true).childOf(::visuals).asParent()
    private val font by segmented("Font", TextHud.HudFont.Minecraft).childOf(::visuals)
    private val fontScale by slider("Font scale", 1f, 0.5f, 3f, 0.1f).childOf(::visuals)
    private val scale by slider("Map scale", 5f, 1f, 10f, 0.5f).childOf(::visuals)
    private val roomRadius by slider("Room radius", 5f, 1f, 10f, 1f).childOf(::visuals)
    val roomInCol by colourPicker("Highlight colour", Colour.GREY.withAlpha(0.5f), allowAlpha = true, desc = "Room the player currently in colour.").childOf(::visuals)

//    private val icons by switch("Icons")
//    private val showHeads by switch("Show player heads").childOf(::icons)
//    private val showOwnHead by switch("Show own head").childOf(::showHeads)
//    private val iconScale by slider("Icon scale", 1f, 0.1f, 3.0f, 0.1f).childOf(::showHeads)
//    private val iconBorder by switch("Border").json("Icon border").childOf(::showHeads)
//    private val classColour by switch("Class border colour").childOf(::iconBorder)
//    private val iconBorderColour by colourPicker("Border colour", Colour.BLACK).json("Icon border colour").childOf(::iconBorder) { !classColour }
//    private val iconBorderThickness by slider("Border thickness", 2, 1, 10, unit = "px").json("Icon border thickness").childOf(::iconBorder)
//
//    private val showNames by switch("Show names").childOf(::icons)
//    private val nameScale by slider("Name scale", 0.8f, 0.1f, 3.0f, 0.1f).childOf(::showNames)

    private val mapOpen: Boolean
        get() = mc.screen?.title?.string == "quoi clear map"

    init {
        on<PacketEvent.Received, ClientboundForgetLevelChunkPacket> {
            if (keepChunks) cancel()
        }
    }

    private fun map() = aboba("quoi clear map") {
//        val iconCfg = MapRenderer.IconConfig(
//            scale = iconScale,
//            heads = showHeads,
//            ownHead = showOwnHead,
//            border = iconBorder,
//            borderColour = iconBorderColour,
//            classColour = classColour,
//            thickness = iconBorderThickness,
//            name = showNames,
//            whenLeap = false,
//            nameScale = nameScale
//        )

        val cfg = MapRenderer.MapConfig(
            scale = scale,
            radius = roomRadius,
            font = font.selected.get(),
            fontScale = fontScale,
            shadow = shadow,
            autoClear = true,
//            icons = icons,
//            icon = iconCfg
        )

        renderMap(config = cfg)
    }
}