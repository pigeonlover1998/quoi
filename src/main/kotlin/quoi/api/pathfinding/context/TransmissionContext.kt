package quoi.api.pathfinding.context

import net.minecraft.core.BlockPos
import quoi.api.pathfinding.TeleportPathNode
import quoi.api.pathfinding.util.Raycasts
import quoi.utils.WorldUtils.etherwarpable

class TransmissionContext(
    goal: BlockPos,
    dist: Double,
    hWeight: Double,
    raycasts: Raycasts,
    timeout: Long,
    val ground: Boolean
) : TeleportContext(goal, dist, hWeight, raycasts, timeout) {
    override fun addNode(node: TeleportPathNode) {
        if (!ground) {
            super.addNode(node)
            return
        }

        if (!node.pos.below().etherwarpable) {
            val airNode = TeleportPathNode(
                node.x,
                node.y,
                node.z,
                node.pos,
                node.g + 50.0,
                node.h,
                node.parent,
                node.yaw,
                node.pitch
            )
            super.addNode(airNode)
        } else {
            super.addNode(node)
        }
    }
}