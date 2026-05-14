package quoi.module.impl.dungeon.autoclear.executor.nodes

import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.vec.MutableVec3
import quoi.module.impl.dungeon.autoclear.executor.ClearNode
import quoi.utils.skyblock.item.TeleportUtils.getTeleportPos

class ClearAotvNode(
    pos: Vec3,
    yaw: Float,
    pitch: Float,
    colour: Colour,
    points: List<Vec3>,
) : ClearNode(pos, yaw, pitch, colour, points) {

    override fun execute(playerPos: MutableVec3) = doTeleport(playerPos) { from ->
        from.getTeleportPos(yaw, pitch, 12.0)
    }

}