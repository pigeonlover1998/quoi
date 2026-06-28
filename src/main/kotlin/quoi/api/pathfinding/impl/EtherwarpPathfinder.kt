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
import quoi.api.world.Direction
import quoi.utils.ChatUtils.modMessage
import quoi.utils.WorldUtils.etherwarpable
import quoi.utils.blockPos
import quoi.utils.distanceTo
import quoi.utils.distanceToSqr
import quoi.utils.skyblock.item.TeleportUtils.getEtherwarpDirection
import quoi.utils.skyblock.item.TeleportUtils.traverseVoxels
import kotlin.math.abs

/**
 * A* pathfinder using etherwarp ability
 */
object EtherwarpPathfinder : AbstractTeleportPathfinder<EtherwarpContext>() {

    private var lastDist = -1.0
    private var lastPitchStep = -1.0f
    private var lastYawStep = -1.0f
    private var cachedRaycasts: Raycasts? = null

    /**
     * Finds a path of teleports from [from] to [to]
     * @param offset if true, offsets the teleport y position by 0.05
     * @param withLast if true, includes the goal node in the path
     */
    fun findPath(
        from: Vec3,
        to: BlockPos,
        config: PathConfig = PathConfig(),
        dist: Double = 60.0,
        offset: Boolean = true,
        withLast: Boolean = false
    ): List<TeleportPathNode>? {
        if (!to.etherwarpable) return null
        val raycasts = getRaycasts(dist, config.pitchStep, config.yawStep)
        val ctx = EtherwarpContext(to, dist, config.hWeight, raycasts, config.timeout, offset)
        val startPos = from.blockPos

        ctx.addNode(TeleportPathNode(from.x, from.y, from.z, startPos, 0.0, startPos.distanceTo(to) / dist, null, 0f, 0f))

        val path = find(ctx, config.threads)

        return if (path != null) {
            val smoothed = smoothPath(path, dist, withLast)
            val size = if (withLast) path.size else path.size - 1
            if (config.feedback) modMessage("Found path in ${System.currentTimeMillis() - ctx.startTime}ms (${ctx.processed.get()}). $size || ${smoothed.size}")
            smoothed
        } else {
            if (config.feedback) modMessage("&cFailed &rafter ${System.currentTimeMillis() - ctx.startTime}ms (${ctx.processed.get()}).")
            null
        }
    }

    /**
     * Same as [findPath], but optimised for dungeons.
     * Navigates room by room using [DungeonMapPathfinder]
     */
    fun findDungeonPath(
        from: Vec3,
        to: BlockPos,
        config: PathConfig = PathConfig(),
        dist: Double = 60.0,
        offset: Boolean = true
    ): List<TeleportPathNode>? {
        if (!to.etherwarpable) return null

        val startTime = System.currentTimeMillis()
        var processed = 0

        val startPos = from.blockPos
        val startRoom = ScanUtils.getRoomFromPos(from.x, from.z)
        val goalRoom = ScanUtils.getRoomFromPos(to.x, to.z)

        if (startRoom == null || goalRoom == null || startRoom == goalRoom) {
            return findPath(from, to, config, dist, offset)
        }

        val roomPath = DungeonMapPathfinder.findPath(startRoom, goalRoom) ?: return null

        val path = mutableListOf<TeleportPathNode>()

        var lastNode = TeleportPathNode(from.x, from.y, from.z, startPos, 0.0, 0.0, null, 0f, 0f) // last node in the whole path
        var startNode = lastNode  // start node in *this* segment

        // start - door 1, door 1 - door 2 .. door N - goal
        for (i in roomPath.indices) {
            val step = roomPath[i]

            var target = to
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
                if (config.feedback) modMessage("&cFailed segment ${i + 1}/${roomPath.size} after ${System.currentTimeMillis() - startTime}ms ($processed).")
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

        path.add(0, TeleportPathNode(from.x, from.y, from.z, startPos, 0.0, 0.0, null, 0f, 0f))

        if (path.size > 1) {
            path[1].parent = path[0]
        }

        val smoothed = smoothPath(path, dist)
        if (config.feedback) modMessage("Found &epath&r in ${System.currentTimeMillis() - startTime}ms ($processed). ${path.size} || ${smoothed.size}")

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

    override fun getSneak(): Boolean = true

    override fun getNodeY(ctx: EtherwarpContext, hit: BlockPos): Double =
        hit.y + (if (ctx.offset) 1.05 else 1.0)

    override fun getDirection(from: Vec3, to: BlockPos, dist: Double): Direction? =
        getEtherwarpDirection(from, to, dist)

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