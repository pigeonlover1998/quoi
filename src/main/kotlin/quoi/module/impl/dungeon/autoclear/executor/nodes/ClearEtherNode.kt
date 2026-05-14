package quoi.module.impl.dungeon.autoclear.executor.nodes

import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.vec.MutableVec3
import quoi.module.impl.dungeon.autoclear.executor.ClearNode
import quoi.utils.skyblock.item.TeleportUtils.getEtherPos

class ClearEtherNode(
    pos: Vec3,
    yaw: Float,
    pitch: Float,
    colour: Colour,
    points: List<Vec3>
) : ClearNode(pos, yaw, pitch, colour, points) {

    override fun execute(playerPos: MutableVec3) = doTeleport(playerPos, sneak = true, yOff = 1.05) { from ->
        from.getEtherPos(yaw, pitch)
    }
}