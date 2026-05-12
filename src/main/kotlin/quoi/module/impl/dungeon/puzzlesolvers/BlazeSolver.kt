package quoi.module.impl.dungeon.puzzlesolvers

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Puzzle
import quoi.api.skyblock.dungeon.PuzzleStatus
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.utils.*
import quoi.utils.EntityUtils.renderPos
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawLine
import quoi.utils.render.drawStyledBox
import quoi.utils.skyblock.item.TeleportUtils.getEtherwarpDirection
import quoi.utils.skyblock.item.ItemUtils.hasTerminator
import quoi.utils.skyblock.item.ItemUtils.isShortbow
import quoi.utils.skyblock.player.PlayerUtils.at
import quoi.utils.skyblock.player.PlayerUtils.useItem
import quoi.utils.skyblock.player.SwapManager
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/dungeon/puzzlesolvers/BlazeSolver.kt
 */
object BlazeSolver { // todo maybe improve terminator shit some day
    private var blazes = mutableListOf<ArmorStand>()
    private var roomType = 0
    private var lastBlazeCount = 10
    private val blazeHealthRegex = Regex("^\\[Lv15] ♨ Blaze [\\d,]+/([\\d,]+)❤$")

    private var lastShotTime = 0L
    private var waitingForUpdate = false
    private var currentTarget: ArmorStand? = null
    private var repositionTicker: Ticker? = null
    private var currentSpot = 0

    private val higherSpots = listOf(BlockPos(10, 94, 19), BlockPos(22, 88, 17), BlockPos(10, 118, 23), BlockPos(20, 85, 11))
    private val lowerSpots = listOf(BlockPos(24, 62, 14), BlockPos(9, 45, 18), BlockPos(10, 68, 23), BlockPos(24, 29, 16), BlockPos(13, 50, 8), BlockPos(24, 48, 17))

    fun getBlaze() {
        if (!Dungeon.inDungeons || Dungeon.currentRoom?.name?.equalsOneOf("Lower Blaze", "Higher Blaze") == false) return
        val hpMap = mutableMapOf<ArmorStand, Int>()
        blazes.clear()
        EntityUtils.getEntities<ArmorStand>().forEach { entity ->
            if (entity in blazes) return@forEach
            val hp = blazeHealthRegex.find(entity.name.string)?.groups?.get(1)?.value?.replace(",", "")?.toIntOrNull() ?: return@forEach
            hpMap[entity] = hp
            blazes.add(entity)
        }
        if (Dungeon.currentRoom?.name == "Lower Blaze") blazes.sortByDescending { hpMap[it] }
        else blazes.sortBy { hpMap[it] }
    }

    fun onRoomEnter(room: OdonRoom?) = with(room) {
        if (!this?.name.equalsOneOf("Lower Blaze", "Higher Blaze") ) return@with reset()
    }

    fun onRenderWorld(ctx: WorldRenderContext, blazeLineNext: Boolean, blazeLineAmount: Int, blazeStyle: String, blazeFirstColour: Colour, blazeSecondColour: Colour, blazeAllColour: Colour, blazeAnnounce: Boolean, blazeLineWidth: Float, autoReposition: Boolean) {
        if (!Dungeon.currentRoom?.name.equalsOneOf("Lower Blaze", "Higher Blaze") || blazes.isEmpty()) return

        if (autoReposition) {
            val spots = if (Dungeon.currentRoom?.name == "Higher Blaze") higherSpots else lowerSpots
            spots.forEach { spot ->
                ctx.drawFilledBox(Dungeon.currentRoom!!.getRealCoords(spot).aabb, colour = Colour.CYAN, depth = true)
            }
        }

        blazes.removeAll { mc.level?.getEntity(it.id) == null }

        if (blazes.isEmpty() && lastBlazeCount == 1) {
            Dungeon.puzzles.find { it == Puzzle.BLAZE }?.status = PuzzleStatus.Completed
            if (blazeAnnounce) ChatUtils.command("pc Blaze done!")
            if (autoReposition) mc.options.keyShift.isDown = false
            lastBlazeCount = 0
            return
        }
        lastBlazeCount = blazes.size
        blazes.forEachIndexed { index, entity ->
            val colour = when (index) {
                0 -> blazeFirstColour
                1 -> blazeSecondColour
                else -> blazeAllColour
            }
            val aabb = entity.boundingBox.inflate(0.5, 1.0, 0.5).move(0.0, -1.0, 0.0)

            ctx.drawStyledBox(blazeStyle, aabb, colour, depth = true)

            if (blazeLineNext && index > 0 && index <= blazeLineAmount)
                ctx.drawLine(listOf(blazes[index - 1].renderPos, aabb.center), colour = colour, thickness = blazeLineWidth, depth = true)
        }
    }

    fun onTick(player: LocalPlayer, shootCd: Long, missCd: Long, autoReposition: Boolean) {
        val room = Dungeon.currentRoom ?: return
        if (!room.name.equalsOneOf("Lower Blaze", "Higher Blaze") || blazes.isEmpty()) return
        if (mc.screen != null) return

        repositionTicker?.let {
            if (it.tick()) scheduleTask { repositionTicker = null }
        }
        if (repositionTicker != null) return

        if (room.name == "Higher Blaze" && player.y <= 75) {
            if (autoReposition) cyclePosition(player, room)
            return
        }

        val currentTime = System.currentTimeMillis()
        val target = currentTarget

        if (waitingForUpdate && (target == null || target.isRemoved || mc.level?.getEntity(target.id) == null)) {
            waitingForUpdate = false
            currentTarget = null
        }

        if (waitingForUpdate) {
            val dist = target?.position()?.distanceTo(player.position()) ?: 0.0
            val travelTime = dist / 2.5 * 50.0
            if (currentTime - lastShotTime > travelTime + missCd) {
                waitingForUpdate = false
            } else {
                return
            }
        }

        val blaze = blazes.firstOrNull() ?: return
        val hitboxes = blazes.map { BlazeHitbox(getAABB(it, it == blaze), it == blaze) }

        val hitDir = canHit(player.eyePosition, hitboxes, player.hasTerminator)

        if (hitDir == null) {
            if (autoReposition) cyclePosition(player, room)
            return
        }

        if (!player.mainHandItem.isShortbow) return
        if (currentTime - lastShotTime < shootCd) return

        val finalTarget = player.eyePosition.add(hitDir.getLook().scale(10.0))
        val dir = getDirection(player.eyePosition, finalTarget)
        player.useItem(dir)

        lastShotTime = currentTime
        waitingForUpdate = true
        currentTarget = blaze
    }

    private fun canHit(eyePos: Vec3, hitboxes: List<BlazeHitbox>, isTerminator: Boolean): Direction? {
        val target = hitboxes.firstOrNull { it.isTarget } ?: return null
        val (cx, cy, cz) = target.aabb.center

        val testPoints = listOf(
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

        val dist = (center.x - from.x).pow(2) + (center.z - from.z).pow(2)

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

            for (box in hitboxes) {
                if (box.aabb.clip(currPos, nextPos).isPresent) return box.isTarget
            }

            px = nextX
            py = nextY
            pz = nextZ

            val currDist = (px - from.x) * (px - from.x) + (pz - from.z) * (pz - from.z)
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
        val spots = if (room.name == "Higher Blaze") higherSpots else lowerSpots
        val hitboxes = blazes.map { BlazeHitbox(getAABB(it, it == blaze), it == blaze) }
        val isTerm = player.hasTerminator

        var fallback: BlockPos? = null

        for (j in spots.indices) {
            val i = (currentSpot + j + 1) % spots.size
            val realSpot = room.getRealCoords(spots[i])
            val spotEye = realSpot.vec3.add(0.5, 1.62, 0.5)

            if (fallback == null && getEtherwarpDirection(realSpot) != null) {
                fallback = realSpot
            }

            if (canHit(spotEye, hitboxes, isTerm) == null) continue

            if (getEtherwarpDirection(realSpot) != null) {
                currentSpot = i
                reposition(player, realSpot)
                return
            }

            for (link in spots) {
                val realLink = room.getRealCoords(link)
                if (getEtherwarpDirection(realLink) == null) continue

                val linkEye = realLink.vec3.add(0.5, 1.62, 0.5)
                if (getEtherwarpDirection(linkEye, realSpot) != null) {
                    currentSpot = i
                    reposition(player, realLink)
                    return
                }
            }
        }
        fallback?.let { reposition(player, it) }
    }

    private fun reposition(player: LocalPlayer, spot: BlockPos) {
        if (repositionTicker != null) return

        repositionTicker = ticker {
            val r = SwapManager.swapById("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END").success
            if (!mc.options.keyShift.isDown) {
                action {
                    mc.options.keyShift.isDown = true
                }
                delay(2)
            }
            action {
                if (!r) cancel()
                val dir = getEtherwarpDirection(spot) ?: cancel()
                player.useItem(dir)
            }
            await { player.at(spot) }
            action {
                SwapManager.swapByLore("Shortbow: Instantly shoots!")
            }
        }
    }

    fun reset() {
        lastBlazeCount = 10
        blazes.clear()
        roomType = 0

        lastShotTime = 0
        currentTarget = null
        repositionTicker = null
        waitingForUpdate = false
        currentSpot = 0
    }

    private data class BlazeHitbox(val aabb: AABB, val isTarget: Boolean)
}