package quoi.module.impl.dungeon.puzzlesolvers.impl

import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.DungeonEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.Event
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Puzzle
import quoi.api.skyblock.dungeon.PuzzleStatus
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.world.Direction
import quoi.module.impl.dungeon.autoclear.executor.ClearExecutor
import quoi.module.impl.dungeon.puzzlesolvers.PuzzleSolvers
import quoi.module.impl.dungeon.puzzlesolvers.Repositionable
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.group.SettingGroup
import quoi.utils.*
import quoi.utils.EntityUtils.getEntities
import quoi.utils.EntityUtils.getEntity
import quoi.utils.EntityUtils.renderPos
import quoi.utils.Scheduler.scheduleLoop
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawLine
import quoi.utils.render.drawStyledBox
import quoi.utils.skyblock.item.ItemUtils.hasTerminator
import quoi.utils.skyblock.item.ItemUtils.isShortbow
import quoi.utils.skyblock.item.TeleportUtils.getEtherwarpDirection
import quoi.utils.skyblock.player.PlayerUtils.useItem
import kotlin.math.cos
import kotlin.math.sin

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/dungeon/puzzlesolvers/BlazeSolver.kt
 */
object Blaze : SettingGroup(PuzzleSolvers, "Blaze"), Repositionable {

    private val solver by switch("Solver", desc = "Shows the solution for the blaze puzzle.")
    private val lineNext by switch("Next line", desc = "Shows the next line to click.").childOf(::solver)
    private val lineAmount by slider("Lines amount", 1, 1, 10, 1, desc = "Amount of lines to show.").childOf(::lineNext)
    private val lineWidth by slider("Lines width", 2f, 0.5f, 5f, 0.1f, desc = "Width for blaze lines.").childOf(::lineNext)
    private val style by selector("Style", "Box", arrayListOf("Box", "Filled box"), desc = "Render style to be used.").childOf(::solver)
    private val firstColour by colourPicker("First colour", Colour.MINECRAFT_GREEN.withAlpha(0.75f), desc = "Colour for the first blaze.").childOf(::solver)
    private val secondColour by colourPicker("Second colour", Colour.MINECRAFT_GOLD.withAlpha(0.75f), desc = "Colour for the second blaze.").childOf(::solver)
    private val allColour by colourPicker("Other colour", Colour.WHITE.withAlpha(0.3f), desc = "Colour for the other blazes.").childOf(::solver)
    private val announce by switch("Announce completion", desc = "Sends complete message.").asParent()
    val auto by switch("Auto")
    private val reposition by switch("Auto reposition").childOf(::auto)

    private var blazes = mutableListOf<ArmorStand>()
    private var lastBlazeCount = 10

    override var repositionTicker: Ticker? = null
    private var lastShotTime = 0L
    private var waitingForUpdate = false
    private var currentTarget: ArmorStand? = null
    private var currentSpot = 0

    init {
        scheduleLoop(10) {
            if (!module.active) return@scheduleLoop
            if (solver || auto) getBlaze()
        }

        on<DungeonEvent.Room.Enter> {
            if (!room?.name.equalsOneOf("Lower Blaze", "Higher Blaze") ) return@on reset()
        }

        on<RenderEvent.World> {
            if (!solver || blazes.isEmpty()) return@on

            if (auto && reposition) {
                val spots = if (Dungeon.currentRoom?.name == "Higher Blaze") HIGHER_SPOTS else LOWER_SPOTS
                spots.forEach { spot ->
                    ctx.drawFilledBox(Dungeon.currentRoom!!.getRealCoords(spot).aabb, colour = Colour.CYAN, depth = true)
                }
            }

            blazes.removeAll { it.isRemoved || getEntity(it.id) == null }

            if (blazes.isEmpty() && lastBlazeCount == 1) {
                Dungeon.puzzles.find { it == Puzzle.BLAZE }?.status = PuzzleStatus.Completed
                if (announce) ChatUtils.command("pc Blaze done!")
                if (auto && reposition) mc.options.keyShift.isDown = false
                lastBlazeCount = 0
                return@on
            }

            lastBlazeCount = blazes.size

            blazes.forEachIndexed { index, entity ->
                val colour = when (index) {
                    0 -> firstColour
                    1 -> secondColour
                    else -> allColour
                }
                val aabb = entity.boundingBox.inflate(0.5, 1.0, 0.5).move(0.0, -1.0, 0.0)

                ctx.drawStyledBox(style.selected, aabb, colour, depth = true)

                if (lineNext && index in 1..lineAmount)
                    ctx.drawLine(listOf(blazes[index - 1].renderPos, aabb.center), colour = colour, thickness = lineWidth, depth = true)
            }
        }

        on<TickEvent.End> {
            if (!auto || ClearExecutor.active || blazes.isEmpty() || mc.screen != null) return@on
            val room = Dungeon.currentRoom ?: return@on

            repositionTicker?.let {
                if (it.tick()) Scheduler.scheduleTask { repositionTicker = null }
            }
            if (repositionTicker != null) return@on

            if (room.name == "Higher Blaze" && player.y <= 75) {
                if (reposition) cyclePosition(player, room)
                return@on
            }

            val currentTime = System.currentTimeMillis()
            val target = currentTarget

            if (waitingForUpdate && (target == null || target.isRemoved || getEntity(target.id) == null)) {
                waitingForUpdate = false
                currentTarget = null
            }

            if (waitingForUpdate) {
                val dist = target?.position()?.distanceTo(player.position()) ?: 0.0
                val travelTime = dist / 2.5 * 50.0
                if (currentTime - lastShotTime > travelTime + PuzzleSolvers.missCd) {
                    waitingForUpdate = false
                } else return@on
            }

            val blaze = blazes.firstOrNull() ?: return@on
            val hitboxes = blazes.map { BlazeHitbox(getAABB(it, it == blaze), it == blaze) }

            val hitDir = canHit(player.eyePosition, hitboxes, player.hasTerminator)

            if (hitDir == null) {
                if (reposition) cyclePosition(player, room)
                return@on
            }

            if (!player.mainHandItem.isShortbow || currentTime - lastShotTime < PuzzleSolvers.shootCd) return@on

            val finalTarget = player.eyePosition.add(hitDir.look().scale(10.0))
            val dir = getDirection(player.eyePosition, finalTarget)
            player.useItem(dir)

            lastShotTime = currentTime
            waitingForUpdate = true
            currentTarget = blaze
        }

        on<WorldEvent.Change> {
            reset()
        }
    }

    override fun shouldHandle(event: Event): Boolean {
        if (!super.shouldHandle(event)) return false

        if (event is DungeonEvent.Room.Enter) return true

        return Dungeon.currentRoom?.name?.equalsOneOf("Lower Blaze", "Higher Blaze") == true
    }

    private fun getBlaze() {
        val room = Dungeon.currentRoom?.name ?: return
        if (!Dungeon.inDungeons || !room.equalsOneOf("Lower Blaze", "Higher Blaze")) return
        val hpMap = mutableMapOf<ArmorStand, Int>()
        blazes.clear()
        getEntities<ArmorStand>().forEach { entity ->
            if (entity in blazes) return@forEach
            val hp = BLAZE_HEALTH_REGEX.find(entity.name.string)?.groups?.get(1)?.value?.replace(",", "")?.toIntOrNull() ?: return@forEach
            hpMap[entity] = hp
            blazes.add(entity)
        }
        if (room == "Lower Blaze") blazes.sortByDescending { hpMap[it] }
        else blazes.sortBy { hpMap[it] }
    }

    private fun canHit(eyePos: Vec3, hitboxes: List<BlazeHitbox>, isTerminator: Boolean): Direction? {
        val target = hitboxes.firstOrNull { it.isTarget } ?: return null
        val (cx, cy, cz) = target.aabb.center

        val testPoints = arrayOf(
            Vec3(cx, cy, cz),
            Vec3(cx, cy + 0.6, cz),
            Vec3(cx, cy - 0.6, cz),
            Vec3(cx + 0.2, cy, cz),
            Vec3(cx - 0.2, cy, cz),
            Vec3(cx, cy, cz + 0.2),
            Vec3(cx, cy, cz - 0.2)
        )

        for (point in testPoints) {
            val dir = getArrowDirection(eyePos, point, isTerminator)
            val (yaw, pitch) = dir
            val origin = getArrowOrigin(eyePos, yaw, isTerminator)

            if (isSafe(origin, yaw, pitch, hitboxes, false)) {

                if (!isTerminator || (isSafe(origin, yaw + 5f, pitch, hitboxes, true) && isSafe(origin, yaw - 5f, pitch, hitboxes, true))) {
                    return dir
                }
            }
        }
        return null
    }

    private fun getAABB(blaze: ArmorStand, isTarget: Boolean): AABB {
        val centre = blaze.boundingBox.center
        val cx = centre.x
        val cy = centre.y - 1.0
        val cz = centre.z

        val width = if (isTarget) 0.35 else 0.75
        val height = if (isTarget) 0.8 else 1.45

        return AABB(
            cx - width, cy - height, cz - width,
            cx + width, cy + height, cz + width
        )
    }

    private fun isSafe(from: Vec3, yaw: Float, pitch: Float, hitboxes: List<BlazeHitbox>, sideArrow: Boolean): Boolean {
        val target = hitboxes.firstOrNull { it.isTarget } ?: return sideArrow
        val center = target.aabb.center

        val dist = (center.x - from.x).sq + (center.z - from.z).sq

        var (px, py, pz) = from
        val yawRad = yaw.rad
        val pitchRad = pitch.rad

        var mx = -sin(yawRad) * cos(pitchRad) * 3.0
        var my = -sin(pitchRad) * 3.0
        var mz = cos(yawRad) * cos(pitchRad) * 3.0

        for (tick in 0..100) {
            val nextX = px + mx
            val nextY = py + my
            val nextZ = pz + mz
            val nextPos = Vec3(nextX, nextY, nextZ)
            val currPos = Vec3(px, py, pz)

            if (!isPathClear(currPos, nextPos)) return sideArrow

            val box = hitboxes.firstOrNull { it.aabb.clip(currPos, nextPos).isPresent }
            if (box != null) return box.isTarget

            px = nextX
            py = nextY
            pz = nextZ

            val currDist = (px - from.x).sq + (pz - from.z).sq
            if (currDist > dist + 30.0) break

            mx *= 0.99
            my = my * 0.99 - 0.05
            mz *= 0.99
        }

        return sideArrow
    }

    private fun cyclePosition(player: LocalPlayer, room: OdonRoom) {
        if (repositionTicker != null) return
        val blaze = blazes.firstOrNull() ?: return
        val spots = if (room.name == "Higher Blaze") HIGHER_SPOTS else LOWER_SPOTS
        val hitboxes = blazes.map { BlazeHitbox(getAABB(it, it == blaze), it == blaze) }
        val isTerm = player.hasTerminator

        var fallback: BlockPos? = null

        for (j in spots.indices) {
            val i = (currentSpot + j + 1) % spots.size
            val realSpot = room.getRealCoords(spots[i])
            val spotEye = realSpot.vec3.add(0.5, 1.62, 0.5)

            val dir = getEtherwarpDirection(realSpot)

            if (fallback == null && dir != null) {
                fallback = realSpot
            }

            if (canHit(spotEye, hitboxes, isTerm) == null) continue

            if (dir != null) {
                currentSpot = i
                reposition(realSpot, bow = true)
                return
            }

            for (link in spots) {
                val realLink = room.getRealCoords(link)
                if (getEtherwarpDirection(realLink) == null) continue

                val linkEye = realLink.vec3.add(0.5, 1.62, 0.5)
                if (getEtherwarpDirection(linkEye, realSpot) != null) {
                    currentSpot = i
                    reposition(realLink)
                    return
                }
            }
        }
        fallback?.let { reposition(it) }
    }

    private fun reset() {
        lastBlazeCount = 10
        blazes.clear()

        lastShotTime = 0
        currentTarget = null
        repositionTicker = null
        waitingForUpdate = false
        currentSpot = 0
    }

    private val BLAZE_HEALTH_REGEX = Regex("^\\[Lv15] ♨ Blaze [\\d,]+/([\\d,]+)❤$")

    private val HIGHER_SPOTS = listOf(
        BlockPos(10, 94, 19),
        BlockPos(22, 88, 17),
        BlockPos(10, 118, 23),
        BlockPos(20, 85, 11)
    )
    private val LOWER_SPOTS = listOf(
        BlockPos(24, 62, 14),
        BlockPos(9, 45, 18),
        BlockPos(10, 68, 23),
        BlockPos(24, 29, 16),
        BlockPos(13, 50, 8),
        BlockPos(24, 48, 17)
    )

    private data class BlazeHitbox(val aabb: AABB, val isTarget: Boolean)
}