package quoi.module.impl.dungeon

import kotlinx.coroutines.launch
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.player.Input
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.scope
import quoi.api.abobaui.dsl.*
import quoi.api.colour.*
import quoi.api.events.*
import quoi.api.pathfinding.impl.EtherwarpPathfinder
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon.currentRoom
import quoi.api.skyblock.dungeon.Dungeon.inClear
import quoi.api.skyblock.dungeon.Dungeon.isDead
import quoi.api.skyblock.dungeon.odonscanning.MapRenderer
import quoi.api.skyblock.dungeon.odonscanning.MapRenderer.renderMap
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomComponent
import quoi.api.skyblock.dungeon.odonscanning.tiles.Rotations
import quoi.api.skyblock.invoke
import quoi.api.vec.MutableVec3
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.*
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.containsOneOf
import quoi.utils.WorldUtils.etherwarpable
import quoi.utils.WorldUtils.nearbyBlocks
import quoi.utils.WorldUtils.state
import quoi.utils.skyblock.ItemUtils.skyblockId
import quoi.utils.skyblock.player.PlayerUtils.useItem
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.ui.hud.impl.TextHud
import quoi.utils.ui.screens.UIScreen.Companion.open
import kotlin.math.ceil

/**
 * TODO:
 *  room queuing while auto routing
 *  teleport to doors
 *  teleport to minis
 */
object InteractiveMap : Module(
    "Interactive Map",
    desc = "Automatically teleports to a specified target.",
    area = Island.Dungeon(inClear = true),
    tag = Tag.BETA
) {
    private val keepChunks by switch("Keep chunks loaded", true, desc = "Keeps the chunks loaded. Good for long distances.")
    private val rightToStart by switch("Right click for start", desc = "Teleports to auto route start node on mouse right click.")

    private val openKey by keybind("Open key")
        .onPress {
            if (!enabled || !inClear || isDead) return@onPress
            if (mc.screen?.title?.string == "quoi clear map" && closeOn.index == 1) {
                mc.setScreen(null)
            } else if (mc.screen == null) {
                open(map(), background = false)
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

    private val sett by text("Pathfinder settings")
    private val yawStep by slider("Yaw step", 22f, 10f, 30f, desc = "Horizontal density of raycasts. Lower values increase precision but reduce performance.").childOf(::sett)
    private val pitchStep by slider("Pitch step", 22f, 10f, 30f, desc = "Vertical density of raycasts. Lower values increase precision but reduce performance.").childOf(::sett)
    private val hWeight by slider("Guess weight", 6.7, 1.0, 15.0, 0.1, desc = "Higher values make the search much faster; due to path smoothing, the difference in final path quality is negligible.").childOf(::sett)
    private val threads by slider("Threads", 4, 1, 16, desc = "Number of CPU threads to use for simultaneous path expansion.").childOf(::sett)
    private val timeout by slider("Timeout", 1000L, 1000L, 2000L, 50L, unit = "ms", desc = "Maximum time allowed for the pathfinder to search before giving up.").childOf(::sett)

    private var nodes: MutableList<ClearNode>? = null

    private var delay = 0
    private var postDelay = 0
    var active = false
        private set

    private var pending: Direction? = null
    private var position: MutableVec3? = null

    private val roomOverrides = mapOf(
        "Creeper Beams" to BlockPos(15, 68, 5),
        "Three Weirdos" to BlockPos(15, 68, 22),
        "Water Board" to BlockPos(15, 58, 9),
        "Ice Path" to BlockPos(10, 67, 8),
        "Tic Tac Toe" to BlockPos(11, 68, 16),
        "Ice Fill" to BlockPos(15, 69, 7),
        "Quiz" to BlockPos(15, 68, 5),
        "Boulder" to BlockPos(15, 68, -2),
        "Old Trap" to BlockPos(15, 68, -2),
        "New Trap" to BlockPos(15, 68, -2),
        "Cages" to BlockPos(15, 64, 16)
    )

    private val coreOverrides = mapOf( // suboptimal if room rot is none, todo find a different way to find goal
        "Gold" to mapOf(
            35550104 to BlockPos(5, 68, 15),
            992885012 to BlockPos(55, 68, 15)
        ),
        "Layers" to mapOf(
            161195688 to BlockPos(53, 68, 53)
        ),
        "Mage" to mapOf(
            925853313 to BlockPos(15, 75, 15)
        ),
        "Deathmite" to mapOf(
            706341009 to BlockPos(5, 68, 15)
        ),
        "Dragon" to mapOf(
            -1334473473 to BlockPos(15, 68, 18)
        )
    )
    
    init {
        on<PacketEvent.Received> {
            when (packet) {
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

            if (postDelay > 0) {
                if (--postDelay == 0) active = false
            }

            if (delay != 0) return@on

            if (postDelay == 0) active = false

            val nodes = nodes ?: return@on
            if (nodes.isEmpty()) return@on

            if (position == null) {
                position = MutableVec3(player.x, player.y, player.z)
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
            nodes = null
            delay = 0
            position = null
            active = false
            postDelay = 0
        }
    }

    private fun handleQueue(stupid: MutableVec3, nodes: MutableList<ClearNode>): Boolean {
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
                postDelay = 2
            }
            return true
        }

        return false
    }

    fun getPath(room: OdonRoom, comp: RoomComponent, button: Int) {
        if (!player.onGround()) return
        if (button != 0 && button != 1) return
        if (currentRoom?.name?.containsOneOf("Maze", "Boulder") == true) return
        if (currentRoom?.name?.contains("Trap") == true && currentRoom!!.getRelativeCoords(player.blockPosition()).z >= 0) return

        var start = BlockPos(player.x, ceil(player.y - 1), player.z)
        var dir = getEtherwarpDirection(start)

        if (dir == null) {
            start = start.nearbyBlocks(4f).find { pos ->
                pos.etherwarpable && getEtherwarpDirection(pos).also { dir = it } != null
            } ?: return modMessage("Could not find a valid etherwarpable block nearby.")
        }

        val goal = when(button) {
            0 -> {
                val overridePos = roomOverrides[room.name] ?: coreOverrides[room.name]?.get(comp.core)

                if (overridePos != null && room.rotation != Rotations.NONE) {
                    room.getRealCoords(overridePos)
                } else {
                    comp.blockPos.nearbyBlocks(25f) { it.etherwarpable && it.state.block != Blocks.REDSTONE_BLOCK }.firstOrNull()
                        ?: return modMessage("Couldn't find goal position for tile &e${comp.core}&r in ${room.name}")
                }
            }
            1 -> {
                if (!rightToStart) return
                val rings = AutoRoutes.routeNodes[room.name] ?: return modMessage("No rings found in &e${room.name}")
                val starts = rings.filter { it.start == true }

                val target = starts.map { room.getRealCoords(BlockPos(it.relative.x, ceil(it.relative.y), it.relative.z).below()) }
                    .filter { it.etherwarpable }
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
                offset = true,
                dist = 60.0
            ) ?: return@launch

            val new = mutableListOf<ClearNode>()

            new.add(ClearNode(player.position(), dir!!.yaw, dir.pitch))

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

    private data class ClearNode(val pos: Vec3, val yaw: Float, val pitch: Float) {

        fun inside(stupid: MutableVec3): Boolean {
            val dx = pos.x - stupid.x
            val dy = pos.y - stupid.y
            val dz = pos.z - stupid.z
            return dx.sq + dy.sq + dz.sq <= 0.1
        }

        fun execute(stupid: MutableVec3): Boolean {
            if (player.mainHandItem.skyblockId != "ASPECT_OF_THE_VOID") return false
            if (!player.lastSentInput.shift) return false

            val from = Vec3(stupid.x, stupid.y + getEyeHeight(true), stupid.z)
            val ether = from.getEtherPos(yaw, pitch)

            if (!ether.succeeded || ether.pos == null) {
                nodes = null
                position = null
                postDelay = 2
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