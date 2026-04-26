package quoi.module.impl.dungeon

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.scope
import quoi.api.autoroutes.RouteRing
import quoi.api.autoroutes.actions.*
import quoi.api.autoroutes.registerCommands
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.commands.internal.BaseCommand
import quoi.api.events.*
import quoi.api.events.core.EventBus
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon.currentRoom
import quoi.api.skyblock.dungeon.Dungeon.inClear
import quoi.api.skyblock.invoke
import quoi.config.ConfigMap
import quoi.config.configMap
import quoi.config.typeName
import quoi.config.typedEntries
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.ChatUtils
import quoi.utils.ChatUtils.literal
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.wait
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.StringUtils.width
import quoi.utils.WorldUtils.state
import quoi.utils.aabb
import quoi.utils.render.*
import quoi.utils.render.DrawContextUtils.drawText
import quoi.utils.scaledHeight
import quoi.utils.scaledWidth
import quoi.utils.skyblock.player.RotationUtils.pitch
import quoi.utils.skyblock.player.RotationUtils.yaw
import kotlin.coroutines.cancellation.CancellationException

object AutoRoutesLegacy : Module(
    "Auto Routes Legacy",
    desc = "/lroute",
    area = Island.Dungeon(inClear = true),
    tag = Tag.LEGACY
) {
    val zeroTickDb by switch("Zero tick dungeon breaker")
    private val style by selector("Style", "Box", listOf("Box", "Filled box", "Cylinder"))
    private val multicolour by switch("Multicolour")
    private val colour by colourPicker("Colour", Colour.CYAN).visibleIf { !multicolour }
    private val fillColour by colourPicker("Fill colour", Colour.CYAN.withAlpha(0.5f), allowAlpha = true).visibleIf { style.selected == "Filled box" && !multicolour }
    private val activeCol by colourPicker("Active colour", Colour.WHITE)

    val actionEntries by lazy { typedEntries<RingAction>() }

    private val colourDropdown by text("Colours").visibleIf { multicolour }
    private val colours = actionEntries.associate { (name, action) ->
        val set = colourPicker(name, action().colour).childOf(::colourDropdown)
        this.register(set)
        name to set
    }
    private val fillColourDropdown by text("Fill colours").visibleIf { style.selected == "Filled box" && multicolour }
    private val fillColours = actionEntries.associate { (name, action) ->
        val set = colourPicker(name, action().colour.withAlpha(0.5f), allowAlpha = true).json("$name fill").childOf(::fillColourDropdown)
        this.register(set)
        name to set
    }
    private val thickness by slider("Thickness", 4f, 1f, 8f, 0.5f)
    val height by slider("Height", 0.1f, 0.1f, 1f, 0.1f)
    private val interactDelay by slider("Interact delay", 2, 0, 6, 1, unit = "t")

    val routes: ConfigMap<String, MutableList<RouteRing>> by configMap("auto_routes.json")

    // todo make internal shit less schizo
    internal var editMode = false
    internal var currentChain: String? = null

    internal val currentRings = hashSetOf<RouteRing>()
    val visitedRings = hashSetOf<RouteRing>()
    val completedChainNodes = hashSetOf<RouteRing>()
    val completedAwaits = hashSetOf<RouteRing>()

    private val awaitingRings = hashSetOf<RouteRing>()
    val batIds = hashSetOf<Int>()
    var secretsAwaited = 0
    private var shouldDelay = false

    private var currentJob: Job? = null
    internal val removedRings = mutableMapOf<String, MutableList<List<Pair<Int, RouteRing>>>>()
    internal val addHistory = mutableMapOf<String, MutableList<RouteRing>>()

    internal val breakerCache = mutableMapOf<DungeonBreakerAction, List<Pair<BlockPos, AABB>>>()
    internal var breakerRing: RouteRing? = null
    internal var interactListener: EventBus.EventListener? = null
    internal var lastClickedBlock: BlockPos? = null

    internal val ar = BaseCommand("lroute").requires("&cEnable the module and be in a dungeon!") { enabled && inClear && currentRoom != null }
    internal val add = ar.sub("add").description("Adds specified ring.").suggests("add", actionEntries.map { it.first })

    init {
        registerCommands()
        on<TickEvent.End> {
            if (AutoClear.active) return@on
            val room = currentRoom ?: return@on
            val rings = routes[room.data.name] ?: return@on
            currentRings.clear()
            rings.filterTo(currentRings) { it.inside(room) }
            lastClickedBlock = null

            if (editMode || currentJob?.isActive == true) return@on

            visitedRings.retainAll(currentRings)
            awaitingRings.retainAll(currentRings)

            val prioritisedRings = rings.sortedByDescending {
                when (it.action) {
                    is DungeonBreakerAction -> 150
                    is BoomAction -> 100
                    is UseItemAction -> 50
                    else -> 0
                }
            }

            for (ring in prioritisedRings) {
                if (ring !in currentRings) continue
                if (ring in visitedRings) continue
                if (!ring.checkArgs()) continue

                if (!ring.chain.isNullOrEmpty()) {
                    val chainRings = rings.filter { it.chain == ring.chain }
                    val index = chainRings.indexOf(ring)

                    if (index == 0) {
                        val nextRings = chainRings.drop(1).toSet()
                        completedChainNodes.removeAll(nextRings)
                    } else {
                        if (ring in completedChainNodes) continue
                        val parent = chainRings[index - 1]
                        if (parent !in completedChainNodes) continue
                    }
                }

                visitedRings.add(ring)
                completedChainNodes.add(ring)
                awaitingRings.remove(ring)

                currentJob = scope.launch {
                    runCatching {
                        ring.delay?.let {
                            delay(it.toLong())
                            if (!ring.inside(room)) return@launch
                        }

                        ring.action.execute(player)
                    }.onFailure { error ->
                        if (error is CancellationException) return@launch
                        modMessage(
                            ChatUtils.button(
                                "&cError occurred while executing ring &e${ring.action.typeName} &7(click to copy)",
                                command = "/quoidev copy ${error.stackTraceToString()}",
                                hoverText = "Click to copy"
                            )
                        )
                        error.printStackTrace()
                    }
                }

                break
            }
        }

        on<RenderEvent.World> {
            val room = currentRoom ?: return@on
            val rings = routes[room.data.name] ?: return@on

            val chainPoints = mutableMapOf<String, Vec3>()
            val chainIndices = mutableMapOf<String, Int>()
            val isShitCylinder = style.selected == "Cylinder"

            rings.forEach { ring ->
                if (ring.action is StartAction) return@forEach

                val pos = room.getRealCoords(Vec3(ring.x, ring.y, ring.z))

                val col = if (ring in currentRings) activeCol else ring.colour()
                if (isShitCylinder) {
                    val r = ring.radius.toFloat() / 2
                    ctx.drawCylinder(pos, r, height, col, thickness = thickness, depth = true) // looks like fucking shit
                } else {
                    ctx.drawStyledBox(style.selected, ring.boundingBox(room), col, ring.fillColour(), thickness, depth = true)
                }

                val chain = ring.chain ?: return@forEach

                chainPoints[chain]?.let { prev ->
                    ctx.drawLine(
                        points = listOf(prev, pos),
                        colour = activeCol.withAlpha(100),
                        depth = true,
                        thickness = thickness / 2f
                    )
                }
                chainPoints[chain] = pos

                if (editMode) {
                    val index = chainIndices[chain] ?: 0
                    chainIndices[chain] = index + 1
                    ctx.drawText(
                        text = literal("$index"),
                        pos = pos.add(0.0, 0.3, 0.0),
                        scale = 1.2f,
                        depth = true
                    )
                }
            }

            rings.forEach { ring ->
                if (ring.action is StartAction) {
                    val aabb = ring.boundingBox(room).deflate(0.05)
                    ctx.drawStyledBox(style.selected, aabb, ring.colour(), ring.fillColour(), thickness, depth = false)
                    return@forEach
                }
            }


            val ringsToRender = currentRings.toMutableSet()
            breakerRing?.let { ringsToRender.add(it) }

            ringsToRender.forEach { ring ->
                val action = ring.action as? DungeonBreakerAction ?: return@forEach
                val blocks = breakerCache.getOrPut(action) {
                    action.blocks.map { rel ->
                        val realPos = room.getRealCoords(rel)
                        realPos to realPos.aabb
                    }
                }

                blocks.forEach { (pos, aabb) ->
                    if (pos.state.isAir) {
                        ctx.drawWireFrameBox(aabb, Colour.RED.withAlpha(125), depth = true)
                    } else {
                        ctx.drawFilledBox(aabb, Colour.WHITE.withAlpha(125), depth = true)
                    }
                }
            }
        }

        on<RenderEvent.Overlay> {
            val lines = mutableListOf<String>()
            if (editMode) lines.add("Edit mode")
            if (breakerRing != null) lines.add("&6DB Editor")
            if (currentChain != null) lines.add("&bChain: $currentChain")
            lines.forEachIndexed { i, string ->
                val x = (scaledWidth - string.noControlCodes.width()) / 2f
                val y = scaledHeight / 2f + 10 + i * 10
                ctx.drawText(string, x, y)
            }
        }

        on<MouseEvent.Click> {
            if (button != 0) return@on

            val stupid = currentRings.filter { it in awaitingRings && it !in visitedRings }

            if (stupid.isNotEmpty()) {
                secretsAwaited = 999
                currentJob?.cancel()
            }
        }

        on<WorldEvent.Change> {
            visitedRings.clear()
            completedChainNodes.clear()
            breakerCache.clear()
            currentJob?.cancel()
            addHistory.clear()

            completedAwaits.clear()
            awaitingRings.clear()
            batIds.clear()
            secretsAwaited = 0
        }

        on<DungeonEvent.Secret.Interact> {
            if (awaitingRings.isNotEmpty()) {
                secretsAwaited++
                shouldDelay = true
            }
        }
        on<DungeonEvent.Secret.Item> { if (awaitingRings.isNotEmpty()) secretsAwaited++ }
    }

    fun registerAwait(ring: RouteRing) {
        if (ring in awaitingRings) return

        if (awaitingRings.isEmpty()) {
            secretsAwaited = 0
            batIds.clear()
        }
        awaitingRings.add(ring)
    }

    suspend fun interactDelay() {
        if (shouldDelay) {
            wait(interactDelay)
            shouldDelay = false
        }
    }

    private fun RouteRing.colour() = if (multicolour) colours[this.action.typeName]?.value ?: Colour.WHITE else colour
    private fun RouteRing.fillColour() = if (multicolour) fillColours[this.action.typeName]?.value ?: Colour.WHITE else fillColour

    internal val yaw get() = currentRoom!!.getRelativeYaw(player.yaw)
    internal val pitch get() = player.pitch
}