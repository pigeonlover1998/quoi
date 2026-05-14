package quoi.api.autoroutes.nodes

import net.minecraft.client.player.LocalPlayer
import quoi.api.autoroutes.RouteNode
import quoi.api.colour.Colour
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.vec.MutableVec3
import quoi.config.TypeName
import quoi.utils.skyblock.player.RotationUtils.pitch
import quoi.utils.skyblock.player.RotationUtils.rotate
import quoi.utils.skyblock.player.RotationUtils.yaw

@TypeName("rotate")
class RotateNode : RouteNode() {
    var yaw = 0f
    var pitch = 0f

    @Transient
    private var realYaw = 0f

    override val colour: Colour
        get() = Colour.YELLOW

    override fun update(room: OdonRoom) {
        super.update(room)
        realYaw = room.getRealYaw(yaw)
    }

    override fun execute(player: LocalPlayer, pos: MutableVec3): Boolean {
        player.rotate(realYaw, pitch)
        return true
    }

    override fun create(player: LocalPlayer, room: OdonRoom): RouteNode {
        yaw = room.getRelativeYaw(player.yaw)
        pitch = player.pitch
        return this
    }
}