package quoi.module.impl.dungeon

import quoi.QuoiMod.MOD_ID
import quoi.api.abobaui.dsl.copies
import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.dsl.size
import quoi.api.colour.Colour
import quoi.api.colour.multiply
import quoi.api.events.RenderEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Checkmark
import quoi.api.skyblock.dungeon.DoorState
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.RoomType
import quoi.api.skyblock.dungeon.components.Room
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.*
import quoi.utils.StringUtils.width
import quoi.utils.rad
import quoi.utils.render.DrawContextUtils.drawImage
import quoi.utils.render.DrawContextUtils.drawPlayerHead
import quoi.utils.render.DrawContextUtils.drawString
import quoi.utils.render.DrawContextUtils.hollowRect
import quoi.utils.render.DrawContextUtils.rect
import quoi.utils.render.DrawContextUtils.withMatrix
import quoi.utils.ui.hud.Hud
import quoi.utils.ui.hud.setting
import quoi.utils.ui.hud.withTransform
import quoi.utils.ui.rendering.NVGRenderer.image
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import java.util.*

object DungeonMap : Module(
    "Dungeon Map",
    area = Island.Dungeon,
    desc = "some desc idc"
) {
    private val renderHidden by BooleanSetting("Render hidden rooms")
    private val textRenderType by SelectorSetting("Room names render", "Both", arrayListOf("None", "Names", "Secrets", "Both"))
    private val textScale by NumberSetting("Text scale", 0.5f, 0.1f, 3.0f, 0.1f)
    private val scoreInfo by BooleanSetting("Score info")

    private val bgColour by ColourSetting("Background colour", Colour.RGB(38, 85, 12, 0.8f), allowAlpha = true)
    private val border by BooleanSetting("Border")
    private val borderThickness by NumberSetting("Border thickness", 2, 1, 10, unit = "px").withDependency { border }
    private val borderColour by ColourSetting("Border colour", Colour.RGB(255, 255, 255, 0.0f), allowAlpha = true).withDependency { border }

    private val roomColourDropdown by DropdownSetting("Room colours").collapsible()
    val normalRoom by ColourSetting("Normal", Colour.RGB(107, 58, 17, 1.0f), allowAlpha = true).withDependency(roomColourDropdown)
    val entranceRoom by ColourSetting("Entrance", Colour.RGB(81, 255, 0, 1.0f), allowAlpha = true).withDependency(roomColourDropdown)
    val puzzleRoom by ColourSetting("Puzzle", Colour.RGB(117, 0, 133, 1.0f), allowAlpha = true).withDependency(roomColourDropdown)
    val trapRoom by ColourSetting("Trap", Colour.RGB(216, 127, 51, 1.0f), allowAlpha = true).withDependency(roomColourDropdown)
    val miniRoom by ColourSetting("Mini boss", Colour.RGB(254, 223, 0, 1.0f), allowAlpha = true).withDependency(roomColourDropdown)
    val bloodRoom by ColourSetting("Blood", Colour.RGB(255, 0, 0, 1.0f), allowAlpha = true).withDependency(roomColourDropdown)
    val fairyRoom by ColourSetting("Fairy", Colour.RGB(224, 0, 255, 1.0f), allowAlpha = true).withDependency(roomColourDropdown)
    val rareRoom by ColourSetting("Rare", Colour.MINECRAFT_YELLOW, allowAlpha = true).withDependency(roomColourDropdown)
    val unknownRoom by ColourSetting("Unknown", Colour.MINECRAFT_GRAY, allowAlpha = true).withDependency(roomColourDropdown)
    val darkenMultiplier by NumberSetting("Darken multiplier", 0.4f, 0.0f, 1.0f, 0.1f).withDependency(roomColourDropdown) { renderHidden }

    private val doorColourDropdown by DropdownSetting("Door colours").collapsible()
    val normalDoor by ColourSetting("Normal", Colour.RGB(255, 132, 0, 1.0f), allowAlpha = true).json("Normal door").withDependency(doorColourDropdown)
    val witherDoor by ColourSetting("Wither", Colour.RGB(40, 238, 1, 1.0f), allowAlpha = true).withDependency(doorColourDropdown)
    val bloodDoor by ColourSetting("Blood", Colour.RGB(0, 231, 169, 1.0f), allowAlpha = true).json("Blood door").withDependency(doorColourDropdown)
    val entranceDoor by ColourSetting("Entrance", Colour.RGB(81, 255, 0, 1.0f), allowAlpha = true).json("Entrance door").withDependency(doorColourDropdown)

    private val iconDropDown by DropdownSetting("Icon").collapsible()
    private val smoothMovement by BooleanSetting("Smooth movement").withDependency(iconDropDown)
    private val showHeads by BooleanSetting("Show player heads").withDependency(iconDropDown)
    private val showOwnHead by BooleanSetting("Show own head").withDependency(iconDropDown) { showHeads }
    private val iconScale by NumberSetting("Icon scale", 0.8f, 0.1f, 3.0f, 0.1f).withDependency(iconDropDown) { showHeads }
    private val classColour by BooleanSetting("Class border colour").withDependency(iconDropDown) { showHeads }
    private val iconBorderColour by ColourSetting("Border colour", Colour.BLACK).json("Icon border colour").withDependency(iconDropDown) { !classColour && showHeads }
    private val iconBorderThickness by NumberSetting("Border thickness", 2, 1, 10, unit = "px").json("Icon border thickness").withDependency(iconDropDown) { showHeads }

    private val hud by Hud("Dungeon Map", toggleable = false) {
        group(size(defaultMapSize.first.px, defaultMapSize.second.px)) {
            if (preview) {
                image(
                    image = "default_map.png".image(),
                    copies()
                )
            }
        }
    }.setting()

    init {
        on<RenderEvent.Overlay> {
            hud.withTransform(ctx) {
                ctx.renderMap()
            }
        }
    }

    private val defaultMapSize = Pair(138, 138)

    private const val ROOM = 18
    private const val GAP = 4
    private const val SPACING = ROOM + GAP
    private const val HALF = ROOM / 2

    private val WHITE_MARKER = ResourceLocation.fromNamespaceAndPath(MOD_ID, "white_marker.png")
    private val GREEN_MARKER = ResourceLocation.fromNamespaceAndPath(MOD_ID, "green_marker.png")

    private fun GuiGraphics.renderMap() {
        val mapOffset = if (Dungeon.floor?.floorNumber == 1) 10.6f else 0f
        val mapScale = getScale(Dungeon.floor?.floorNumber)

        withMatrix {
            val (w, bh) = defaultMapSize
            val h = bh + if (scoreInfo) 10 else 0

            rect(0, 0, w, h, bgColour.rgb)
            if (border) hollowRect(0, 0, w, h, borderThickness, borderColour.rgb)

            pose().translate(5.0f, 5.0f)
            pose().translate(mapOffset, 0f)
            pose().scale(mapScale, mapScale)

            renderRooms()
            renderPlayers()
        }
    }

    private fun GuiGraphics.renderRooms() {
        if (!renderHidden) Dungeon.discoveredRooms.values.forEach { (x, z) ->
            rect(x * SPACING, z * SPACING, ROOM, ROOM, Colour.MINECRAFT_DARK_GRAY.rgb)
        }

        Dungeon.uniqueRooms.forEach { room ->
            val colour =
                if (room.explored)
                    room.type.colour.rgb
                else if (renderHidden)
                    room.type.colour.rgb.multiply(1 - darkenMultiplier)
                else return@forEach

            renderRoom(room, colour)
        }

        Dungeon.uniqueDoors.forEach { door ->
            if (door.state == DoorState.UNDISCOVERED && !renderHidden) return@forEach
            val vert = door.rotation == 0
            val (cx, cz) = door.componentPos
            rect(
                (cx / 2 * SPACING) + if (vert) 6 else 18,
                (cz / 2 * SPACING) + if (vert) 18 else 6,
                if (vert) 6 else 4, if (vert) 4 else 6,
                door.type.colour.rgb
            )
        }
    }

    private fun GuiGraphics.renderRoom(room: Room, colour: Int) {
        for ((x, z) in room.components) {
            val px = x * SPACING
            val pz = z * SPACING
            rect(px, pz, ROOM, ROOM, colour)
            if (room.hasComponent(x + 1, z)) rect(px + ROOM, pz, GAP, ROOM, colour)
            if (room.hasComponent(x, z + 1)) rect(px, pz + ROOM, ROOM, GAP, colour)
        }

        if (room.shape == "2x2" && room.components.size == 4) {
            val minX = room.components.minOf { it.first }
            val minZ = room.components.minOf { it.second }
            rect(minX * SPACING + ROOM, minZ * SPACING + ROOM, GAP, GAP, colour)
        }

        renderNames(room)
    }

    private fun GuiGraphics.renderNames(room: Room) {
        val mode = when {
            room.type == RoomType.PUZZLE -> 1
            room.type.isNormal() -> textRenderType.index
            else -> 0
        }

        val lines = buildList {
            if (mode and 1 != 0) addAll(room.name.split(" "))
            if (mode and 2 != 0 && room.secrets != 0) {
                val count = if (room.checkmark == Checkmark.GREEN) room.secrets else room.secretsFound
                add("$count/${room.secrets}")
            }
        }

        if (lines.isEmpty()) return

        val (cx, cz) = room.centre()
        val x = cx * SPACING + HALF
        val y = cz * SPACING + HALF
        val scale = 0.75f * textScale

        withMatrix(x, y, scale) {

            val colour = room.checkmark.colorCode
            var dy = -(lines.size * 9) / 2

            for (line in lines) {
                val dx = -line.width() / 2
                drawString(colour + line, dx.toInt(), dy)
                dy += 9
            }
        }
    }

    private fun GuiGraphics.renderPlayers() {

        Dungeon.dungeonTeammates.sortedBy { it.name == player.name.string }.forEach  { p ->
            if (p.isDead && p.name != player.name.string) return@forEach

            val pos = if (smoothMovement) p.pos.getLerped() else p.pos.raw
            val ix = pos?.iconX?.toFloat() ?: return@forEach
            val iz = pos.iconZ?.toFloat() ?: return@forEach
            val rot = pos.yaw?.toFloat() ?: 0f

            val x = ix / 125.0f * 128.0f
            val y = iz / 125.0f * 128.0f

            withMatrix(x, y) {
                pose().rotate(rot.rad)
                pose().scale(iconScale, iconScale)

                if (!showOwnHead && p.name == player.name.string) {
                    drawImage(GREEN_MARKER, -4, -5, 7, 10)
                } else if (showHeads) {
                    val colour = if (classColour) p.colour else iconBorderColour
                    rect(-6, -6, 12, 12, colour.rgb)
                    pose().scale(1f - iconBorderThickness, 1f - iconBorderThickness)
                    drawPlayerHead(p.uuid ?: UUID(0, 0), -6, -6, 12)
                } else {
                    drawImage(WHITE_MARKER, -4, -5, 7, 10)
                }
            }

        }
    }


    private fun getScale(floor: Int?) = when (floor) {
        0 -> 6f / 4f
        in 1..3 -> 6f / 5f
        else -> 1f
    }
}