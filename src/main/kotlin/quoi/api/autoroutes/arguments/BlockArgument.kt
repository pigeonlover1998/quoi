package quoi.api.autoroutes.arguments

import quoi.api.autoroutes.RouteRing
import quoi.api.skyblock.dungeon.Dungeon
import quoi.config.TypeName
import quoi.utils.WorldUtils.registryName
import quoi.utils.WorldUtils.state
import net.minecraft.core.BlockPos

@TypeName("block")
class BlockArgument(val name: String = "", val blockPos: BlockPos = BlockPos(0, 0, 0)) : RingArgument {
    override fun check(ring: RouteRing): Boolean {
        val currentRoom = Dungeon.currentRoom ?: return false
        val blockPos = currentRoom.getRealCoords(blockPos)
        return blockPos.state?.block?.registryName != name
    }

}