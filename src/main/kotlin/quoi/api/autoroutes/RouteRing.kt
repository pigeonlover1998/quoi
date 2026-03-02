package quoi.api.autoroutes

import quoi.QuoiMod.mc
import quoi.api.autoroutes.actions.RingAction
import quoi.api.autoroutes.arguments.RingArgument
import quoi.api.skyblock.dungeon.components.Room
import quoi.utils.component1
import quoi.utils.component2
import quoi.utils.component3
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.module.impl.dungeon.AutoRoutes

data class RouteRing(
    val x: Double,
    val y: Double,
    val z: Double,
    val action: RingAction,
    val arguments: List<RingArgument>? = null,
    val radius: Double = 1.0,
    val delay: Int? = null,
    val chain: String? = null
) {

    fun boundingBox(room: OdonRoom? = null): AABB {
        val r = radius / 2
        val (x, y, z) = room?.let { room.getRealCoords(Vec3(x, y, z)) } ?: Vec3(x, y, z)
        return AABB(
            x - r, y, z - r,
            x + r, y + AutoRoutes.height, z + r
        )
    }

    fun inside(room: OdonRoom): Boolean {
        val player = mc.player ?: return false
        return boundingBox(room).intersects(player.boundingBox)
    }

    fun checkArgs() = arguments?.all { it.check(this) } ?: true
}