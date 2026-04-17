package quoi.module.impl.dungeon

import kotlinx.coroutines.launch
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.scope
import quoi.api.abobaui.AbobaUI
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.abobaui.elements.impl.RefreshableGroup
import quoi.api.abobaui.elements.impl.refreshableGroup
import quoi.api.animations.Animation
import quoi.api.colour.*
import quoi.api.events.PacketEvent
import quoi.api.events.RenderEvent
import quoi.api.events.WorldEvent
import quoi.api.pathfinding.impl.EtherwarpPathfinder
import quoi.api.pathfinding.impl.EtherwarpPathfinder.blackListed
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.ScanUtils
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomType
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.StringUtils.width
import quoi.utils.ThemeManager.theme
import quoi.utils.Vec2i
import quoi.utils.WorldUtils.etherwarpable
import quoi.utils.WorldUtils.nearbyBlocks
import quoi.utils.WorldUtils.state
import quoi.utils.aabb
import quoi.utils.addVec
import quoi.utils.render.DrawContextUtils.drawText
import quoi.utils.render.DrawContextUtils.rect
import quoi.utils.render.DrawContextUtils.withMatrix
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawLine
import quoi.utils.ui.hud.impl.TextHud
import quoi.utils.ui.rendering.NVGRenderer
import quoi.utils.ui.screens.UIScreen.Companion.open

// todo figure post patch 0t
object AutoClear : Module(
    "Auto clear",
//    area = Island.Dungeon(inClear = true)
) {
    private val font by segmented("Font", TextHud.HudFont.Minecraft)
    private val scale by slider("Scale", 5f, 1f, 10f, 0.5f, desc = "Map scale")
    private val closeOnRelease by switch("Close on release")//.visibleIf { !closeOnRepress }
    private val closeOnRepress: Boolean by switch("Close on repress")//.visibleIf { !closeOnRelease }
    private val openKey by keybind("Open key")
        .onPress {
            if (!enabled /* || !inClear*/) return@onPress
            if (mc.screen?.title?.string == "quoi clear map" && closeOnRepress) {
                mc.setScreen(null)
            } else if (mc.screen == null) {
                map = map()
                open(map, background = false)
            }
        }
        .onRelease {
            if (!enabled || !closeOnRelease/* || !inClear*/) return@onRelease
            if (mc.screen?.title?.string == "quoi clear map") mc.setScreen(null)
        }

    private val sett by text("Pathfinder settings").open()
    private val yawStep by slider("Yaw step", 22f, 10f, 30f).childOf(::sett)
    private val pitchStep by slider("Pitch step", 22f, 10f, 30f).childOf(::sett)
    private val hWeight by slider("Guess weight", 6.7, 1.0, 15.0, 0.1, desc = "Higher = faster, but worse paths").childOf(::sett)
    private val threads by slider("Threads", 2, 1, 16).childOf(::sett)
    private val timeout by slider("Timeout", 1000L, 1000L, 2000L, 50L, unit = "ms").childOf(::sett)
    
    init {
        on<RenderEvent.Overlay> {
            val x = 30f
            val y = 150f
            ctx.pose().pushMatrix()
            ctx.pose().scale(1.75f, 1.75f)
            ctx.pose().translate(x, y)
            ctx.drawMap()
            ctx.pose().popMatrix()
        }

        on<PacketEvent.Received, ClientboundMapItemDataPacket> {
            mc.execute { rescan(packet) }
        }

        on<RenderEvent.World> {
            val points = points ?: return@on
            val path = path ?: return@on
            path.forEach { pos ->
                ctx.drawFilledBox(pos.aabb, colour = Colour.ORANGE.withAlpha(100), depth = true)
            }
            ctx.drawLine(points, colour = Colour.WHITE, depth = true)
        }

        on<WorldEvent.Change> {
            startCoords = null
            roomSize = null
            path = null
            points = null
        }

//        command.sub("nword") {
//            map = map()
//            open(map, background = false)
//        }
    }

    private var path: List<BlockPos>? = null
    private var points: List<Vec3>? = null

    private var map: AbobaUI.Instance = map()

    private var refreshableMap: RefreshableGroup? = null

    private fun map() = aboba("quoi clear map") {
        block(
            size(Bounding + (4 * scale).px, Bounding + (4 * scale).px),
            colour = Colour.BLACK.withAlpha(100),
            (scale * 1.5f).radius()
        ) {
            refreshableMap = refreshableGroup(constrain(Centre, Centre, Bounding, Bounding)) {

                ScanUtils.scannedDoors.forEach { door ->
                    val pos = door.placement
                    val size = door.size

                    block(
                        constrain(x = (pos.x * scale).px, y = (pos.z * scale).px, w = (size.x * scale).px, h = (size.z * scale).px),
                        colour = colour { door.colour.rgb }
                    )
                }

                ScanUtils.scannedRooms.distinctBy { it.roomComponents.firstOrNull()?.placement }.forEach { room ->
                    val components = room.roomComponents
                    if (components.isEmpty()) return@forEach
                    val coords = components.map { it.x to it.z }.toSet()

                    val c = {
                        val base = room.data.colour
                        if (Dungeon.currentRoom == room) base.mix(Colour.GREY, 0.5f).rgb else base.rgb
                    }
                    val col = Colour.Animated(
                        from = colour { c() },
                        to = colour { c().multiply(1.15f) }
                    )

                    val outlineCol = Colour.Animated(
                        from = Colour.TRANSPARENT,
                        to = colour { Colour.WHITE.withAlpha(180).rgb }
                    )

                    components.forEach { comp ->
                        val pos = comp.placement
                        fun has(dx: Int, dz: Int) = (comp.x + dx to comp.z + dz) in coords

                        val north = has(0, -32)
                        val south = has(0, 32)
                        val west  = has(-32, 0)
                        val east  = has(32, 0)

                        val nw = has(-32, -32)
                        val ne = has(32, -32)
                        val sw = has(-32, 32)
                        val se = has(32, 32)

                        val w = (if (east) 20 else 16) * scale + 1f
                        val h = (if (south) 20 else 16) * scale + 1f

                        block(
                            constrain(x = (pos.x * scale).px, y = (pos.z * scale).px, w = w.px, h = h.px),
                            colour = col,
                            radius = radius(
                                tl = if (north || west) 0 else scale,
                                bl = if (south || west) 0 else scale,
                                br = if (south || east) 0 else scale,
                                tr = if (north || east) 0 else scale
                            )
                        ) {
                            outline(outlineCol, 2.px)

                            fun mask(side: Boolean, vert: Boolean, p: Float, s1: Boolean, d1: Boolean, s2: Boolean, d2: Boolean) {
                                if (!side) return
                                val a = if (s1) (if (d1) -0.5f else 4.15f * scale) else 1f
                                val b = if (s2) (if (d2) -0.5f else 4.15f * scale) else 1f
                                val rect =
                                    if (vert)
                                        constrain(p.px, a.px, 5.px, (h - a - b).px)
                                    else
                                        constrain(a.px, p.px, (w - a - b).px, 5.px)
                                block(rect, colour = col)
                            }

                            mask(north, false, -2f,    west,  nw, east,  ne)
                            mask(south, false, h - 3f, west,  sw, east,  se)
                            mask(west,  true,  -2f,    north, nw, south, sw)
                            mask(east,  true,  w - 3f, north, ne, south, se)

                            onMouseEnterExit {
                                col.animate(0.15.seconds, style = Animation.Style.EaseOutQuint)
                                outlineCol.animate(0.15.seconds, style = Animation.Style.EaseOutQuint)
                            }
                            onClick {
                                getPath(room)
                                true
                            }
                        }
                    }

                    if (room.data.type in listOf(RoomType.ENTRANCE, RoomType.FAIRY, RoomType.BLOOD)) return@forEach

                    val lines = room.name.split(" ")
                    val textSize = (theme.textSize.pixels * 1.2f) * (scale / 4f)
                    val font = font.selected.get()

                    lines.forEachIndexed { i, s ->
                        val tw = if (font.name == "Minecraft") s.width(textSize / mc.font.lineHeight) else NVGRenderer.textWidth(s, textSize, font)
                        val tx = (room.textPlacement.x * scale) + 8f * scale - (tw / 2f)
                        val ty = (room.textPlacement.z * scale) + 8f * scale - (lines.size * textSize / 2f) + (i * textSize)

                        text(
                            string = s,
                            size = textSize.px,
                            colour = colour { room.textColour.rgb },
                            font = font,
                            pos = at(x = tx.px, y = ty.px)
                        )
                    }
                }
            }
        }
    }

    private fun getPath(room: OdonRoom) {
        val start = player.blockPosition().below()

        // todo check if door to that room is closed
        val goal = room.roomComponents.first().blockPos.nearbyBlocks(20f) { it.etherwarpable && !it.state.blackListed && it.state.block != Blocks.REDSTONE_BLOCK }.firstOrNull() ?: return // idk

//        modMessage("${room.name} $goal")

        scope.launch {
            val p = EtherwarpPathfinder.findPath(
                start = start,
                goal = goal,
                yawStep = yawStep,
                pitchStep = pitchStep,
                hWeight = hWeight,
                threads = threads,
                timeout = timeout
            ) ?: return@launch
            path = p
            points = path!!.map { it.center.addVec(y = 0.5) }
        }

    }

    private fun GuiGraphics.drawMap() {

        ScanUtils.scannedDoors.forEach { door ->
            val pos = door.placement
            val size = door.size
            rect(pos.x, pos.z, size.x, size.z, door.colour.rgb)
        }
        
        ScanUtils.scannedRooms.forEach { room ->
            val col = room.data.colour.rgb

            room.roomComponents.forEach { component ->
                val pos = component.placement

                val east = room.roomComponents.any { it.x == component.x + 32 && it.z == component.z }
                val south = room.roomComponents.any { it.x == component.x && it.z == component.z + 32 }

                val w = if (east) 20 else 16
                val h = if (south) 20 else 16

                rect(pos.x, pos.z, w, h, col)
            }

            if (room.data.type in listOf(RoomType.ENTRANCE, RoomType.FAIRY, RoomType.BLOOD)) return@forEach

            val pos = room.textPlacement
            val lines = room.name.split(" ")

            lines.forEachIndexed { i, s ->
                drawText(s, pos.x + 8f - s.width(0.25f), pos.z + 8f - (lines.size * 2.25f) + (i * 4.5f), room.textColour.rgb, 0.5f)
            }
        }

        val px = ((player.x + 185.0) / 32.0 * 20.0) + 8.0
        val pz = ((player.z + 185.0) / 32.0 * 20.0) + 8.0

        withMatrix(px, pz) {
            rect(-1.5, -1.5, 3, 3, Colour.WHITE.rgb)
        }
    }

    private var startCoords: Vec2i? = null
    private var roomSize: Int? = null

    fun rescan(packet: ClientboundMapItemDataPacket) {
        if (!Dungeon.inDungeons || packet.mapId.id and 1000 != 0) return
        val state = mc.level?.getMapData(packet.mapId) ?: return
        val colors = state.colors

        if (startCoords == null) {
            val (greenStart, greenLength) = findGreenRoom(colors)
            if (greenLength != 16 && greenLength != 18) return

            roomSize = greenLength
            startCoords = Vec2i(
                (greenStart and 127) % (greenLength + 4),
                (greenStart shr 7) % (greenLength + 4)
            )
        }

        val rs = roomSize ?: return
        val tileSize = rs + 4
        val startCenter = startCoords!!.add(Vec2i(rs / 2, rs / 2))

        ScanUtils.scannedRooms.forEach { room ->
            var finalColor = 0
            for (component in room.roomComponents) {
                val gx = (component.x + 185) / 32
                val gz = (component.z + 185) / 32

                val mapX = startCenter.x + gx * tileSize
                val mapY = startCenter.z + gz * tileSize
                val index = mapY * 128 + mapX

                if (index in colors.indices) {
                    val color = colors[index].toInt() and 0xFF
                    if (color != 0) {
                        finalColor = color
                        break
                    }
                }
            }
            if (finalColor != 0) room.updateState(finalColor)
        }

        ScanUtils.scannedDoors.forEach { door ->
            val gx = (door.pos.x + 185).toDouble() / 32.0
            val gz = (door.pos.z + 185).toDouble() / 32.0

            val mapX = (startCenter.x + gx * tileSize).toInt()
            val mapY = (startCenter.z + gz * tileSize).toInt()
            val index = mapY * 128 + mapX

            if (index in colors.indices) {
                val color = colors[index].toInt() and 0xFF
                if (color != 0) {
                    door.updateState(color)
                }
            }
        }
    }

    private fun findGreenRoom(mapData: ByteArray): Pair<Int, Int> {
        var start = -1
        var length = 0
        for (i in mapData.indices) {
            if (mapData[i].toInt() == 30) {
                if (length++ == 0) start = i
            } else {
                if (length >= 16) return start to length
                length = 0
            }
        }
        return start to length
    }
}