package quoi.api.autoroutes2.nodes

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import quoi.QuoiMod.mc
import quoi.api.autoroutes2.RouteNode
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.vec.MutableVec3
import quoi.config.TypeName
import quoi.module.impl.dungeon.AutoRoutes
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.WorldUtils.state
import quoi.utils.aabb
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawWireFrameBox
import quoi.utils.skyblock.ItemUtils.getBreakerCharges
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.skyblock.player.interact.AuraManager

@TypeName("dungeon_breaker")
class BreakerNode : RouteNode() {
    var blocks= emptyList<BlockPos>()

    @Transient
    private var active = false

    @Transient
    private var realBlocks = emptyList<BlockPos>()

    override val colour: Colour
        get() = Colour.ORANGE

    override val priority: Int
        get() = 150

    override fun update(room: OdonRoom) {
        super.update(room)
        realBlocks = blocks.map { room.getRealCoords(it) }
    }

    override fun checkAwaits(player: LocalPlayer): Boolean {
        if (!inside(player)) return false
        return super.checkAwaits(player)
    }

    override fun execute(player: LocalPlayer, pos: MutableVec3): Boolean {
        if (active) return false
        if (blocks.isEmpty()) return true
        val breakerSlot = PlayerUtils.breakerSlot ?: return true

        val toBreak = realBlocks.filter { pos ->
            mc.level!!.isLoaded(pos) &&
            !pos.state.isAir //&&
//            pos.distToCenterSqr(player.eyePosition()) <= 30.0
        }

        if (toBreak.isEmpty()) return true

        if (player.inventory.selectedSlot != breakerSlot) {
            return !SwapManager.swapToSlot(breakerSlot).success
        }

        val initialCharges = getBreakerCharges(player.inventory.getItem(breakerSlot))
        if (initialCharges == 0) return true

        active = true

        if (AutoRoutes.zeroTickDb) {
            toBreak.forEach { AuraManager.breakBlock(it, immediate = true) }
            active = false
            return true
        } else {
            toBreak.forEachIndexed { i, pos ->
                if (i >= initialCharges) {
                    active = false
                    return true
                }
                scheduleTask(i) { AuraManager.breakBlock(pos, immediate = true) }
            }
            scheduleTask(realBlocks.size) { active = false }
            return false
        }
    }

    override fun render(ctx: WorldRenderContext, style: String, colour: Colour, fillColour: Colour, activeColour: Colour, thickness: Float) {
        super.render(ctx, style, colour, fillColour, activeColour, thickness)
        if (!inside(mc.player!!) && AutoRoutes.breakerRing == null) return
        for (pos in realBlocks) {
            val aabb = pos.aabb
            if (pos.state.isAir) {
                ctx.drawWireFrameBox(aabb, Colour.RED.withAlpha(125), depth = true)
            } else {
                ctx.drawFilledBox(aabb, Colour.WHITE.withAlpha(125), depth = true)
            }
        }
    }
}