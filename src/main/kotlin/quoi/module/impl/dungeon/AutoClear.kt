package quoi.module.impl.dungeon

import kotlinx.coroutines.launch
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket
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
import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.refreshableGroup
import quoi.api.animations.Animation
import quoi.api.autoroutes.actions.StartAction
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
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomComponent
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomType
import quoi.api.skyblock.invoke
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.*
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.containsOneOf
import quoi.utils.StringUtils.width
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
 *  make it not require to stand on etherwarpable
 *  add players to map
 *  improve goal finder:
 *    - Mage: dead body secret side component
 *    - Deathmite: last secret side component
 *    - Puzzles: ether to start puzzle positions for auto to trigger
 *  clearable rooms highlight:
 *    -
 *  auto mobs clear maybe (star mob esp needs a recode for it)
 *  .
 *  auto auto clear:
 *   -room queuing while auto routing option
 *   -auto room queueing option
 *  .
 *  change this fucking scanner
 *
 */
object AutoClear : Module(
    "Auto clear",
    desc = "Automatically teleports to a specified room.",
    area = Island.Dungeon(inClear = true)
) {
    private val info by text(
        """
            You &cmust&r stand on etherwarpable
            &cfull&r block for it to work.
            
            &c! &rTHIS MODULE IS NOT FINISHED &c!
                          &c!! &rEXPECT BUGS &c!!
        """.trimIndent()
    )

    private val keepChunks by switch("Keep chunks loaded", true, desc = "Keeps the chunks loaded. Good for long distances.")
    private val rightToStart by switch("Right click for start", desc = "Teleports to auto route start node on mouse right click.")

    private val openKey by keybind("Open key")
        .onPress {
            if (!enabled || !inClear || isDead) return@onPress
            if (mc.screen?.title?.string == "quoi clear map" && closeOn.index == 1) {
                mc.setScreen(null)
            } else if (mc.screen == null) {
                map = map()
                open(map, background = false)
            }
        }
        .onRelease {
            if (!enabled || closeOn.index != 0 || !inClear || isDead) return@onRelease
            if (mc.screen?.title?.string == "quoi clear map") mc.setScreen(null)
        }

    private val closeOn by segmented("Close on", "Release", listOf("Release", "Repress"))

    private val visuals by text("Visuals")
    private val shadow by switch("Shadow", true).childOf(::visuals).asParent()
    private val font by segmented("Font", TextHud.HudFont.Minecraft).childOf(::visuals)
    private val fontScale by slider("Font scale", 1f, 0.5f, 3f, 0.1f).childOf(::visuals)
    private val scale by slider("Map scale", 5f, 1f, 10f, 0.5f).childOf(::visuals)
    private val roomInCol by colourPicker("Highlight colour", Colour.GREY.withAlpha(0.5f), allowAlpha = true, desc = "Room the player currently in colour.").childOf(::visuals)

    private val sett by text("Pathfinder settings")
    private val yawStep by slider("Yaw step", 22f, 10f, 30f, desc = "Horizontal density of raycasts. Lower values increase precision but reduce performance.").childOf(::sett)
    private val pitchStep by slider("Pitch step", 22f, 10f, 30f, desc = "Vertical density of raycasts. Lower values increase precision but reduce performance.").childOf(::sett)
    private val hWeight by slider("Guess weight", 6.7, 1.0, 15.0, 0.1, desc = "Higher values make the search much faster; due to path smoothing, the difference in final path quality is negligible.").childOf(::sett)
    private val threads by slider("Threads", 2, 1, 16, desc = "Number of CPU threads to use for simultaneous path expansion.").childOf(::sett)
    private val timeout by slider("Timeout", 1000L, 1000L, 2000L, 50L, unit = "ms", desc = "Maximum time allowed for the pathfinder to search before giving up.").childOf(::sett)

    private var map: AbobaUI.Instance = map()
    private var refreshableMap: RefreshableGroup? = null

    private var nodes: MutableList<ClearNode>? = null

    private var delay = 0
    var active = false
        private set

    private var pending: Direction? = null
    private var position: Stupid? = null
    
    init {
        on<DungeonEvent.Room.Scan> {
            refreshableMap?.refresh()
        }

        on<PacketEvent.Received> {
            when (packet) {
                is ClientboundMapItemDataPacket -> mc.execute { rescan(packet) }
                is ClientboundPlayerPositionPacket -> if (delay == 1) delay = 2
                is ClientboundForgetLevelChunkPacket -> if (keepChunks) cancel()
            }
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

    private fun getPath(room: OdonRoom, comp: RoomComponent, button: Int) {
        if (button != 0 && button != 1) return
        if (Dungeon.currentRoom?.name?.containsOneOf("Trap", "Maze", "Boulder") == true) return

        val start = player.blockPosition().below()

        val state = start.state
        if (state.blackListed || !start.etherwarpable) return modMessage("You &cmust&r stand on a full etherwarpable block! You're standing on ${state.block}")

        val goal = when(button) {
            0 -> comp.blockPos.nearbyBlocks(25f) { it.etherwarpable && !it.state.blackListed && it.state.block != Blocks.REDSTONE_BLOCK }.firstOrNull()
                ?: return modMessage("Couldn't find goal position for component &e#${room.roomComponents.indexOf(comp)}&r in ${room.name}")
            1 -> {
                if (!rightToStart) return
                val rings = AutoRoutes.routes[room.name] ?: return modMessage("No rings found in ${room.name}")
                val starts = rings.filter { it.action is StartAction }

                val target = starts.map { room.getRealCoords(it.pos()).below() }
                    .filter { it.etherwarpable && !it.state.blackListed }
                    .let { pos ->
                        pos.find { it.distanceToSqr(comp.blockPos) < 225 } ?: pos.firstOrNull()
                    } ?: return modMessage("&cCouldn't find start ring.")

                target
            }
            else -> return
        }

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

                ScanUtils.scannedRooms.forEach { room ->
                    val components = room.roomComponents
                    if (components.isEmpty()) return@forEach

                    val c = {
                        val base = room.data.colour
                        if (Dungeon.currentRoom == room) base.mix(roomInCol.withAlpha(255), roomInCol.alpha).rgb else base.rgb
                    }

                    val outlineCol = Colour.Animated(
                        from = Colour.TRANSPARENT,
                        to = Colour.WHITE.withAlpha(180)
                    )

                    val byPos = components.associateBy { it.placement }
                    fun has(p: Vec2i, dx: Int, dz: Int) = byPos.containsKey(p.add(dx, dz))

                    val lCorners = components.filter {
                        val p = it.placement
                        val (n, s, w, e) = listOf(has(p, 0, -20), has(p, 0, 20), has(p, -20, 0), has(p, 20, 0))
                        val long = (n && s) || (w && e)
                        val twoByTwo =
                                    (n && e && has(p, 20, -20)) ||
                                    (n && w && has(p, -20, -20)) ||
                                    (s && e && has(p, 20, 20)) ||
                                    (s && w && has(p, -20, 20))

                        // l corner: 2 connections, not a line, not a part of 2x2
                        listOf(n, s, w, e).count { c -> c } == 2 && !long && !twoByTwo
                    }.map { it.placement }.toSet()


                    components.forEach { comp ->

                        val col = Colour.Animated(
                            from = colour { c() },
                            to = colour { c().multiply(1.15f) }
                        )

                        val p = comp.placement
                        val isL = p in lCorners

                        val (n, s, w, e) = listOf(has(p, 0, -20), has(p, 0, 20), has(p, -20, 0), has(p, 20, 0))

                        // all blocks start at 16x 16
                        // if L corner don't touch
                        // if next to L corner 4
                        // if 2x2 or long 2
                        fun gap(exists: Boolean, dx: Int, dz: Int) = when {
                            !exists || isL -> 0f
                            p.add(dx, dz) in lCorners -> 4f
                            else -> 2f
                        }

                        val ng = gap(n, 0, -20)
                        val sg = gap(s, 0, 20)
                        val wg = gap(w, -20, 0)
                        val eg = gap(e, 20, 0)

                        val width = (16f + wg + eg) * scale
                        val height = (16f + ng + sg) * scale

                        block(
                            constrain(((p.x - wg) * scale).px, ((p.z - ng) * scale).px, width.px, height.px),
                            colour = col,
                            radius = radius(
                                tl = if (n || w) 0 else scale,
                                bl = if (s || w) 0 else scale,
                                br = if (s || e) 0 else scale,
                                tr = if (n || e) 0 else scale
                            )
                        ) {
                            outline(outlineCol, 2.px)

                            // this shit a bit overlaps needed lines
                            // but looks good enough so I'm not fixing it.

                            val mx = wg * scale + 0.5f
                            val my = ng * scale + 0.5f
                            val mw = width - eg * scale - 0.5f
                            val mh = height - sg * scale - 0.5f

                            fun mask(vert: Boolean, start: Float, end: Float, pos: Float) {
                                val constraints =
                                    if (vert)
                                        constrain(pos.px, start.px, 4.px, (end - start).px)
                                    else
                                        constrain(start.px, pos.px, (end - start).px, 4.px)
                                block(constraints, colour = col)
                            }

                            if (n) mask(false,
                                start = if (has(p, -20, -20)) -1f else mx,
                                end   = if (has(p, 20, -20)) width + 1f else mw,
                                pos   = -2f
                            )

                            if (s) mask(false,
                                start = if (has(p, -20, 20)) -1f else mx,
                                end   = if (has(p, 20, 20)) width + 1f else mw,
                                pos   = height - 2f
                            )

                            if (w) mask(true,
                                start = if (has(p, -20, -20)) -1f else my,
                                end   = if (has(p, -20, 20)) height + 1f else mh,
                                pos   = -2f
                            )

                            if (e) mask(true,
                                start = if (has(p, 20, -20)) -1f else my,
                                end   = if (has(p, 20, 20)) height + 1f else mh,
                                pos   = width - 2f
                            )

                            onMouseEnterExit {
                                col.animate(0.15.seconds, style = Animation.Style.EaseOutQuint)
                                outlineCol.animate(0.15.seconds, style = Animation.Style.EaseOutQuint)
                            }
                            onClick(nonSpecific = true) { (button) ->
                                getPath(room, comp, button)
                                true
                            }
                        }
                    }

                    if (room.data.type in listOf(RoomType.ENTRANCE, RoomType.FAIRY, RoomType.BLOOD)) return@forEach

                    val lines = room.name.split(" ")
                    val textSize = 18f * fontScale
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
                        ).shadow = shadow
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
                if (col != 0) {
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
                nodes = null
                position = null
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