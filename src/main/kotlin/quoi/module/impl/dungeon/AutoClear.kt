package quoi.module.impl.dungeon

import kotlinx.coroutines.launch
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.player.Input
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
import quoi.api.events.*
import quoi.api.pathfinding.impl.EtherwarpPathfinder
import quoi.api.pathfinding.impl.EtherwarpPathfinder.blackListed
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Dungeon.floor
import quoi.api.skyblock.dungeon.Dungeon.inClear
import quoi.api.skyblock.dungeon.Dungeon.isDead
import quoi.api.skyblock.dungeon.odonscanning.ScanUtils
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomType
import quoi.api.skyblock.invoke
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.*
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.containsOneOf
import quoi.utils.StringUtils.width
import quoi.utils.ThemeManager.theme
import quoi.utils.WorldUtils.etherwarpable
import quoi.utils.WorldUtils.nearbyBlocks
import quoi.utils.WorldUtils.state
import quoi.utils.skyblock.ItemUtils.skyblockId
import quoi.utils.skyblock.player.PlayerUtils.useItem
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.ui.hud.impl.TextHud
import quoi.utils.ui.rendering.NVGRenderer
import quoi.utils.ui.screens.UIScreen.Companion.open

/**
 * TODO:
 *  fix room states
 *  fix L rooms in small dungeon
 *  fix path executor getting fucked sometimes
 *  add players to map
 *  improve goal finder, it can't find anything in some rooms
 *  add right click to pathfind to start node
 *  clearable rooms highlight
 *  auto auto clear
 *  auto mobs clear maybe (star mob esp needs a recode for it)
 *  .
 *  change this fucking scanner
 *
 */
object AutoClear : Module(
    "Auto clear",
    area = Island.Dungeon(inClear = true)
) {
    private val must_stand by text("You &cmust&r stand on etherwarpable")
    private val to_work by text("block for it to work.")
    private val for_special_people by text("&c! &rTHIS MODULE IS NOT FINISHED &c!")
    private val for_special_people2 by text("              &c!! &rEXPECT BUGS &c!!")

    private val font by segmented("Font", TextHud.HudFont.Minecraft)
    private val scale by slider("Scale", 5f, 1f, 10f, 0.5f, desc = "Map scale")
    private val closeOnRelease by switch("Close on release")//.visibleIf { !closeOnRepress }
    private val closeOnRepress: Boolean by switch("Close on repress")//.visibleIf { !closeOnRelease }
    private val openKey by keybind("Open key")
        .onPress {
            if (!enabled  || !inClear || isDead) return@onPress
            if (mc.screen?.title?.string == "quoi clear map" && closeOnRepress) {
                mc.setScreen(null)
            } else if (mc.screen == null) {
                map = map()
                open(map, background = false)
            }
        }
        .onRelease {
            if (!enabled || !closeOnRelease || !inClear || isDead) return@onRelease
            if (mc.screen?.title?.string == "quoi clear map") mc.setScreen(null)
        }

    private val sett by text("Pathfinder settings").open()
    private val yawStep by slider("Yaw step", 22f, 10f, 30f).childOf(::sett)
    private val pitchStep by slider("Pitch step", 22f, 10f, 30f).childOf(::sett)
    private val hWeight by slider("Guess weight", 6.7, 1.0, 15.0, 0.1, desc = "Higher = faster, but worse paths").childOf(::sett)
    private val threads by slider("Threads", 2, 1, 16).childOf(::sett)
    private val timeout by slider("Timeout", 1000L, 1000L, 2000L, 50L, unit = "ms").childOf(::sett)

    private var map: AbobaUI.Instance = map()
    private var refreshableMap: RefreshableGroup? = null

    private var nodes: MutableList<ClearNode>? = null

    private var delay = 0
    private var active = false

    private var pending: Direction? = null
    private var position: Stupid? = null
    
    init {
        on<DungeonEvent.Room.Scan> {
            refreshableMap?.refresh()
        }

        on<PacketEvent.Received, ClientboundMapItemDataPacket> {
            mc.execute { rescan(packet) }
        }

        on<PacketEvent.Received, ClientboundPlayerPositionPacket> {
            if (delay == 1) delay = 2
        }

        on<TickEvent.Server> {
            if (delay < 2) return@on
            if (delay++ > 9) delay = 0
        }

        on<TickEvent.Start> {

            pending?.let {
                player.useItem(it.yaw, it.pitch)
                pending = null
            }

            if (delay != 0) return@on

            active = false

            val nodes = nodes ?: return@on
            if (nodes.isEmpty()) return@on

            if (position == null) {
                position = Stupid(player.x, player.y, player.z)
            }
            val stupid = position!!

            handleQueue(stupid, nodes)
        }

        on<KeyEvent.Input> {
            if (!active) return@on
            val old = clientInput

            val new = Input(
                old.forward,
                old.backward,
                old.left,
                old.right,
                old.jump,
                true,
                old.sprint
            )

            input.apply(new)
        }

        on<WorldEvent.Change> {
            startCoords = null
            roomSize = null

            nodes = null
            delay = 0
            position = null
        }
    }

    private fun handleQueue(stupid: Stupid, nodes: MutableList<ClearNode>): Boolean {
        val index = nodes.indexOfFirst { it.inside(stupid) }

        if (index < 0) return false

        if (player.mainHandItem.skyblockId != "ASPECT_OF_THE_VOID") {
            if (!SwapManager.swapById("ASPECT_OF_THE_VOID").success) {
                this.nodes = null
                position = null
            }
            return false
        }

        active = true
        val node = nodes[index]

        if (node.execute(stupid)) {
            nodes.removeAt(index)
            if (nodes.isEmpty()) {
                this.nodes = null
                position = null
                delay = 1
            }
            return true
        }

        return false
    }

    private fun getPath(room: OdonRoom) {
        if (Dungeon.currentRoom?.name?.containsOneOf("Trap", "Maze", "Boulder") == true) return
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
                timeout = timeout,
                offset = true
            ) ?: return@launch

            val new = mutableListOf<ClearNode>()
            val dir = getEtherwarpDirection(p[0].pos) ?: return@launch

            new.add(ClearNode(player.position(), dir.yaw, dir.pitch))

            new.addAll(p.dropLast(1).map { node ->
                val pos = node.pos.center.addVec(y = 0.5)
                ClearNode(pos, node.yaw, node.pitch)
            }.toMutableList())

            nodes = new

            position = null
            pending = null
        }

    }

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

                    val outlineCol = Colour.Animated(
                        from = Colour.TRANSPARENT,
                        to = Colour.WHITE.withAlpha(180)
                    )

                    components.forEach { comp ->

                        val col = Colour.Animated(
                            from = colour { c() },
                            to = colour { c().multiply(1.15f) }
                        )

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

    private var startCoords: Vec2i? = null
    private var roomSize: Int? = null

    fun rescan(packet: ClientboundMapItemDataPacket) {
        if (packet.mapId.id and 1000 != 0) return
        val colours = mc.level?.getMapData(packet.mapId)?.colors ?: return

        if (startCoords == null) {
            val (greenStart, greenLength) = findGreenRoom(colours)
            if (greenLength != 16 && greenLength != 18) return

            roomSize = greenLength
            startCoords = when (floor?.floorNumber) {
                0 -> Vec2i(22, 22)
                1 -> Vec2i(22, 11)
                2, 3 -> Vec2i(11, 11)
                else -> Vec2i((greenStart and 127) % (greenLength + 4), (greenStart shr 7) % (greenLength + 4))
            }
        }

        val rs = roomSize ?: return
        val tile = rs + 4
        val centre = startCoords!!.add(Vec2i(rs / 2, rs / 2))

        ScanUtils.scannedRooms.forEach { room ->
            for (component in room.roomComponents) {
                val gx = (component.x + 185) / 32
                val gz = (component.z + 185) / 32

                val mapX = centre.x + gx * tile
                val mapY = centre.z + gz * tile
                val index = mapY * 128 + mapX

                if (index in colours.indices) {
                    val col = colours[index].toInt() and 0xFF
                    if (col != 0) {
                        room.updateState(col)
                        break
                    }
                }
            }
        }

        ScanUtils.scannedDoors.forEach { door ->
            val gx = (door.pos.x + 185).toDouble() / 32.0
            val gz = (door.pos.z + 185).toDouble() / 32.0

            val mapX = (centre.x + gx * tile).toInt()
            val mapY = (centre.z + gz * tile).toInt()
            val index = mapY * 128 + mapX

            if (index in colours.indices) {
                val col = colours[index].toInt() and 0xFF
                if (col != 0 && col != 30) {
                    door.updateState(col)
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

    private data class Stupid(var x: Double, var y: Double, var z: Double)

    private data class ClearNode(val pos: Vec3, val yaw: Float, val pitch: Float) {

        fun inside(stupid: Stupid): Boolean {
            val dx = pos.x - stupid.x
            val dy = pos.y - stupid.y
            val dz = pos.z - stupid.z
            return dx.sq + dy.sq + dz.sq <= 0.1
        }

        fun execute(stupid: Stupid): Boolean {
            if (player.mainHandItem.skyblockId != "ASPECT_OF_THE_VOID") return false
            if (!player.lastSentInput.shift) return false

            val from = Vec3(stupid.x, stupid.y + getEyeHeight(true), stupid.z)
            val ether = from.getEtherPos(yaw, pitch)

            if (!ether.succeeded || ether.pos == null) {
                if (nodes?.last() == this) return true
                modMessage("failed from &c$from &e$yaw $pitch &d$ether")
                return false
            }

            pending = Direction(yaw, pitch)

            stupid.x = ether.pos.x + 0.5
            stupid.y = ether.pos.y + 1.05
            stupid.z = ether.pos.z + 0.5
            return true
        }
    }
}