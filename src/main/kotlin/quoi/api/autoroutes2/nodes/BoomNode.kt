package quoi.api.autoroutes2.nodes

import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import quoi.api.autoroutes2.RouteNode
import quoi.api.colour.Colour
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.vec.MutableVec3
import quoi.config.TypeName
import quoi.utils.addVec
import quoi.utils.equalsOneOf
import quoi.utils.eyePosition
import quoi.utils.getEtherPos
import quoi.utils.getEyeHeight
import quoi.utils.skyblock.ItemUtils.skyblockId
import quoi.utils.skyblock.player.RotationUtils.pitch
import quoi.utils.skyblock.player.RotationUtils.yaw
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.skyblock.player.interact.AuraManager
import quoi.utils.traverseVoxels

@TypeName("boom")
class BoomNode : RouteNode() {
    var yaw = 0f
    var pitch = 0f

    var target: Vec3? = null

    @Transient
    private var realYaw = 0f

    @Transient
    private lateinit var realTarget: Vec3

    override val colour: Colour
        get() = Colour.RED

    override val priority: Int
        get() = 100

    override fun update(room: OdonRoom) {
        super.update(room)
        realYaw = room.getRealYaw(yaw)
        if (target != null) {
            realTarget = room.getRealCoords(target!!)
        }
    }

    override fun execute(player: LocalPlayer, pos: MutableVec3): Boolean {
        val block = if (::realTarget.isInitialized) { // rsa convert compat
            traverseVoxels(this.pos, realTarget, true).pos
        } else {
            pos.immutable().addVec(y = getEyeHeight(true)).getEtherPos(realYaw, pitch, 6.0).pos
        } ?: return true

        if (!player.mainHandItem.skyblockId.equalsOneOf("INFINITE_SUPERBOOM_TNT", "SUPERBOOM_TNT")) {
            return !SwapManager.swapById("INFINITE_SUPERBOOM_TNT", "SUPERBOOM_TNT").success
        }
        AuraManager.interactBlock(block)
        return true
    }

    override fun create(player: LocalPlayer, room: OdonRoom): RouteNode? {
        player.eyePosition(true).getEtherPos(player.yaw, player.pitch, 6.0).pos ?: return null
        yaw = room.getRelativeYaw(player.yaw)
        pitch = player.pitch
        return this
    }
}