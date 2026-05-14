package quoi.api.pathfinding.impl

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.SlabType
import net.minecraft.world.phys.Vec3
import quoi.api.pathfinding.AbstractTeleportPathfinder
import quoi.api.pathfinding.PathConfig
import quoi.api.pathfinding.TeleportPathNode
import quoi.api.pathfinding.context.EtherwarpContext
import quoi.api.pathfinding.util.Raycasts
import quoi.api.pathfinding.util.generateRaycasts
import quoi.api.skyblock.dungeon.odonscanning.ScanUtils
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.utils.ChatUtils.modMessage
import quoi.utils.WorldUtils.etherwarpable
import quoi.utils.blockPos
import quoi.utils.distanceTo
import quoi.utils.distanceToSqr
import quoi.utils.skyblock.player.PlayerUtils.getEyeHeight
import quoi.utils.skyblock.item.TeleportUtils.getEtherwarpDirection
import quoi.utils.skyblock.item.TeleportUtils.traverseVoxels
import kotlin.math.abs

object EtherwarpPathfinder : AbstractTeleportPathfinder<EtherwarpContext>() {

    private var lastDist = -1.0
    private var lastPitchStep = -1.0f
    private var lastYawStep = -1.0f
    private var cachedRaycasts: Raycasts? = null

    fun findPath(
        start: Vec3,
        goal: BlockPos,
        config: PathConfig = PathConfig(),
        dist: Double = 60.0,
        offset: Boolean = true,
        withLast: Boolean = false
    ): List<TeleportPathNode>? {
        if (!goal.etherwarpable) return null
        val raycasts = getRaycasts(dist, config.pitchStep, config.yawStep)
        val ctx = EtherwarpContext(goal, dist, config.hWeight, raycasts, config.timeout, offset)
        val startPos = start.blockPos

        ctx.addNode(TeleportPathNode(start.x, start.y, start.z, startPos, 0.0, startPos.distanceTo(goal) / dist, null, 0f, 0f))

        val path = find(ctx, config.threads)

        return if (path != null) {
            val smoothed = smoothPath(path/*.shiftAndTrim()*/, dist, withLast)
            modMessage("Found path in ${System.currentTimeMillis() - ctx.startTime}ms (${ctx.processed.get()}). ${path.size - 1} || ${smoothed.size}")
            smoothed
        } else {
            modMessage("&cFailed &rafter ${System.currentTimeMillis() - ctx.startTime}ms (${ctx.processed.get()}).")
            null
        }
    }

    fun findDungeonPath(
        start: Vec3,
        goal: BlockPos,
        config: PathConfig = PathConfig(),
        dist: Double = 60.0,
        offset: Boolean = true
    ): List<TeleportPathNode>? {
        if (!goal.etherwarpable) return null

        val startTime = System.currentTimeMillis()
        var processed = 0

        val startPos = start.blockPos
        val startRoom = ScanUtils.getRoomFromPos(start.x, start.z)
        val goalRoom = ScanUtils.getRoomFromPos(goal.x, goal.z)

        if (startRoom == null || goalRoom == null || startRoom == goalRoom) {
            return findPath(start, goal, config, dist, offset)
        }

        val roomPath = DungeonMapPathfinder.findPath(startRoom, goalRoom) ?: return null

        val path = mutableListOf<TeleportPathNode>()

        var lastNode = TeleportPathNode(start.x, start.y, start.z, startPos, 0.0, 0.0, null, 0f, 0f) // last node in the whole path
        var startNode = lastNode  // start node in *this* segment

        // start - door 1, door 1 - door 2 .. door N - goal
        for (i in roomPath.indices) {
            val step = roomPath[i]

            var target = goal
            var radius = 0.0
            var nextRoom: OdonRoom? = null

            if (step.door != null) { // if not last we go to door
                target = BlockPos(step.door.pos.x, 68, step.door.pos.z)
                radius = 9.0
                nextRoom = roomPath[i + 1].room
            }

            val raycasts = getRaycasts(dist, config.pitchStep, config.yawStep)
            val ctx = EtherwarpContext(target, dist, config.hWeight, raycasts, config.timeout, offset, radius, nextRoom)

            startNode.h = startNode.pos.distanceTo(target) / dist
            ctx.addNode(startNode)

            val segment = find(ctx, config.threads)
            processed += ctx.processed.get()

            if (segment == null) {
                modMessage("&cFailed segment ${i + 1}/${roomPath.size} after ${System.currentTimeMillis() - startTime}ms ($processed).")
                return null
            }

            for (j in 1 until segment.size) {
                val node = segment[j]
                val connected = TeleportPathNode(
                    x = node.x,
                    y = node.y,
                    z = node.z,
                    pos = node.pos,
                    g = lastNode.g + node.g,
                    h = node.h,
                    parent = if (j == 1) lastNode else path.last(),
                    yaw = node.yaw,
                    pitch = node.pitch
                )
                path.add(connected)
            }

            if (path.isNotEmpty()) {
                lastNode = path.last()
                startNode = TeleportPathNode(lastNode.x, lastNode.y, lastNode.z, lastNode.pos, 0.0, 0.0, null, lastNode.yaw, lastNode.pitch)
            }
        }

        if (path.isEmpty()) return null

        path.add(0, TeleportPathNode(start.x, start.y, start.z, startPos, 0.0, 0.0, null, 0f, 0f))

        if (path.size > 1) {
            path[1].parent = path[0]
        }

        val smoothed = smoothPath(path, dist)
        modMessage("Found &epath&r in ${System.currentTimeMillis() - startTime}ms ($processed). ${path.size} || ${smoothed.size}")

        return smoothed
    }

    override fun isGoal(ctx: EtherwarpContext, current: TeleportPathNode): Boolean {
        if (ctx.radius > 0.0) {
            if (current.pos.distanceToSqr(ctx.goal) <= ctx.radius) return true

            if (ctx.nextRoom != null) {
                val currentRoom = ScanUtils.getRoomFromPos(current.pos.x, current.pos.z)
                if (currentRoom === ctx.nextRoom && abs(current.pos.y - ctx.goal.y) <= 3) return true
            }
            return false
        }
        return current.pos == ctx.goal
    }

    override fun getEyeY(ctx: EtherwarpContext, node: TeleportPathNode): Double =
        node.y + getEyeHeight(true)

    override fun getNodeY(ctx: EtherwarpContext, hit: BlockPos): Double =
        hit.y + (if (ctx.offset) 1.05 else 1.0)

    override fun getHit(ctx: EtherwarpContext, eyeX: Double, eyeY: Double, eyeZ: Double, dx: Double, dy: Double, dz: Double): BlockPos? {
        val result = traverseVoxels(
            eyeX, eyeY, eyeZ,
            eyeX + dx, eyeY + dy, eyeZ + dz,
            etherwarp = true
        )
        if (result.succeeded && result.pos != null && (result.pos == ctx.goal || !result.state.blackListed)) {
            return result.pos
        }
        return null
    }

    private fun getRaycasts(dist: Double, pitchStep: Float, yawStep: Float): Raycasts {
        if (dist == lastDist && pitchStep == lastPitchStep && yawStep == lastYawStep) {
            cachedRaycasts?.let { return it }
        }

        val raycasts = generateRaycasts(pitchStep, yawStep, dist)

        lastDist = dist
        lastPitchStep = pitchStep
        lastYawStep = yawStep
        cachedRaycasts = raycasts

        return raycasts
    }

    private fun smoothPath(path: List<TeleportPathNode>, dist: Double, withLast: Boolean = false): List<TeleportPathNode> {
        if (path.size < 2) return path

        val smoothed = mutableListOf<TeleportPathNode>()
        var i = 0

        while (i < path.size - 1) {
            var next = i + 1

            val current = path[i]

            val from = Vec3(current.x, current.y + getEyeHeight(true), current.z)

            var yaw = path[next].yaw
            var pitch = path[next].pitch

            for (j in path.size - 1 downTo i + 1) {
                val dir = getEtherwarpDirection(from, path[j].pos, dist)
                if (dir != null) {
                    next = j
                    yaw = dir.yaw
                    pitch = dir.pitch
                    break
                }
            }

            smoothed.add(TeleportPathNode(current.x, current.y, current.z, current.pos, current.g, current.h, current.parent, yaw, pitch))

            i = next
        }

        if (withLast && path.isNotEmpty()) {
            smoothed.add(path.last())
        }

        return smoothed
    }

    inline val BlockState?.blackListed: Boolean
        get() {
            if (this == null) return true
            val isBottomSlab = block is SlabBlock && hasProperty(SlabBlock.TYPE) && getValue(SlabBlock.TYPE) == SlabType.BOTTOM

            return isBottomSlab ||
                    block is CarpetBlock ||
                    block is WallBlock ||
                    block is FenceBlock ||
                    block is FenceGateBlock ||
                    block is HopperBlock ||
                    block is CauldronBlock ||
                    block is BannerBlock
        }
}