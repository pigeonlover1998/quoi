package quoi.api.autoroutes2.nodes

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.mc
import quoi.api.autoroutes2.RouteNode
import quoi.api.colour.Colour
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.vec.MutableVec3
import quoi.config.TypeName
import quoi.module.impl.dungeon.autoclear.impl.AutoRoutes
import quoi.utils.addVec
import quoi.utils.getDirection
import quoi.utils.skyblock.player.PlayerUtils.getEyeHeight
import quoi.utils.getLook
import quoi.utils.rayCastVec
import quoi.utils.render.drawLine
import quoi.utils.skyblock.item.TeleportUtils.traverseVoxels
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.SwapManager

@TypeName("etherwarp")
class EtherwarpNode : RouteNode() {
    var target: Vec3 = Vec3.ZERO

    var yaw: Float? = null // legacy compat
    var pitch: Float? = null

    @Transient
    lateinit var realTarget: Vec3
        private set

    @Transient
    var realYaw: Float? = null

    override val colour: Colour
        get() = Colour.CYAN

    override fun update(room: OdonRoom) {
        super.update(room)

        if (yaw != null && pitch != null) {
            realYaw = room.getRealYaw(yaw!!)
            val from = pos.addVec(y = getEyeHeight(false))
            val to = getLook(realYaw!!, pitch!!).scale(61.0)

            realTarget = rayCastVec(from, to) ?: from.add(to)
            return
        }

        realTarget = room.getRealCoords(target)
    }

    override fun execute(player: LocalPlayer, pos: MutableVec3): Boolean {
//        if (player.mainHandItem.skyblockId != "ASPECT_OF_THE_VOID") {
//            return !SwapManager.swapById("ASPECT_OF_THE_VOID").success
//        }
        if (!player.lastSentInput.shift) return false

        val actualTarget = if (realYaw != null) { // legacy convert compat
            val from = this.pos.addVec(y = getEyeHeight(false))
            val to = getLook(realYaw!!, pitch!!).scale(61.0)
            rayCastVec(from, to) ?: from.add(to)
        } else realTarget

        val from = pos.add(y = getEyeHeight(true)).immutable()
        val direction = actualTarget.subtract(from).normalize()
        val to = actualTarget.add(direction.scale(0.001))

        val ether = traverseVoxels(from, to, true)
        if (!ether.succeeded || ether.pos == null) return false//true

        if (player.mainHandItem.skyblockId != "ASPECT_OF_THE_VOID") {
            return !SwapManager.swapById("ASPECT_OF_THE_VOID").success
        }

        val dir = getDirection(from, actualTarget)

        AutoRoutes.queueInteract(dir)

        pos.x = ether.pos.x + 0.5
        pos.y = ether.pos.y + 1.05
        pos.z = ether.pos.z + 0.5
        return true
    }

    override fun create(player: LocalPlayer, room: OdonRoom): RouteNode? {
        val vec = rayCastVec() ?: return null
        val rel = room.getRelativeCoords(vec)
        target = rel
        return this
    }

    override fun render(ctx: WorldRenderContext, style: String, colour: Colour, fillColour: Colour, activeColour: Colour, thickness: Float) {
        super.render(ctx, style, colour, fillColour, activeColour, thickness)
        val col = if (this.chain != null) Colour.GREEN else Colour.WHITE
        val final = if (yaw != null) Colour.ORANGE else col

        val inside = inside(mc.player!!)
        val actualFinal = if (inside) Colour.CYAN else final
        val thickness = if (inside) 3f else 1.5f
        ctx.drawLine(listOf(pos, realTarget), actualFinal, depth = true, thickness = thickness)
    }

}