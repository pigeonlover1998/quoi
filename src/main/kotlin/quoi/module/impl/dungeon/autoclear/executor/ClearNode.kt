package quoi.module.impl.dungeon.autoclear.executor

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.vec.MutableVec3
import quoi.api.world.RaycastResult
import quoi.module.impl.dungeon.autoclear.executor.nodes.ClearEtherNode
import quoi.utils.ChatUtils.modMessage
import quoi.utils.aabb
import quoi.utils.blockPos
import quoi.utils.equalsOneOf
import quoi.utils.floorPos
import quoi.utils.player
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawLine
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.PlayerUtils.getEyeHeight
import quoi.utils.skyblock.player.SwapManager

abstract class ClearNode(
    val pos: Vec3,
    val yaw: Float,
    val pitch: Float,
    val colour: Colour,
    val points: List<Vec3>,
) {
    open val priority: Int = 0

    abstract fun execute(playerPos: MutableVec3): Boolean

    fun inside(playerPos: MutableVec3): Boolean {
        return playerPos.distanceToSqr(pos) <= 0.1
    }

    open fun render(ctx: WorldRenderContext) {
        val eye = getEyeHeight(this is ClearEtherNode).toDouble()
        ctx.drawFilledBox(pos.aabb(0.1).setMinY(pos.y + 0.1).setMaxY(pos.y + eye), colour = colour.withAlpha(100), depth = true)
        ctx.drawFilledBox(pos.aabb(0.5).setMaxY(pos.y + 0.1).setMinY(pos.y), colour = Colour.GREEN.withAlpha(100), depth = true)
        ctx.drawLine(points, colour = colour, depth = true)
    }

    fun cancel(): Boolean {
        ClearExecutor.cancel()
        return false
    }

    protected fun doTeleport(
        playerPos: MutableVec3,
        items: Array<String> = arrayOf("ASPECT_OF_THE_VOID"),
        sneak: Boolean = false,
        yOff: Double = 1.0,
        block: (Vec3) -> RaycastResult
    ): Boolean {
        if (player.lastSentInput.shift != sneak) return false
//        return false

        if (!player.mainHandItem.skyblockId.equalsOneOf(*items)) {
            if (!SwapManager.swapById(*items).success) return cancel()
        }

        val from = Vec3(playerPos.x, playerPos.y + getEyeHeight(sneak), playerPos.z)
        val res = block(from)

        if (!res.succeeded || res.pos == null) {
//            val next = nodes?.getOrNull((nodes?.indexOf(this) ?: -1) + 1)
//            nodes = null
//            position = null
//            postDelay = 2
            modMessage("""

                    &eFrom&7:&f $from
                    &eTo&7:&r $.{next?.pos}
                    &eYaw&7:&r $yaw
                    &ePitch&7:&r $pitch
                    &eResult&7:&r $res
                """.trimIndent(), prefix = "[&cAutoClear&r]")
            return cancel()
        }

        playerPos.x = res.pos.x + 0.5
        playerPos.y = res.pos.y + yOff
        playerPos.z = res.pos.z + 0.5
        return true
    }

}