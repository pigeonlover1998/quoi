package quoi.module.impl.misc.riftsolvers

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.player.LocalPlayer
import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.utils.BlockPos
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.aabb
import quoi.utils.render.drawFilledBox
import quoi.utils.skyblock.player.MovementUtils.moveTo
import quoi.utils.skyblock.player.MovementUtils.resetInput
import quoi.utils.skyblock.player.PlayerUtils.at
import quoi.utils.skyblock.player.RotationUtils.pitch
import quoi.utils.skyblock.player.RotationUtils.yaw

object LavaMazeSolver {
    private var isActive = false
    private var current = 0
    private var waitingTicks = 0

    private val points = listOf(
        BlockPos(-83.0, 51.0, -112.0),  BlockPos(-86.0, 51.0, -112.0),  BlockPos(-86.0, 51.0, -115.0),
        BlockPos(-88.0, 51.0, -115.0),  BlockPos(-88.0, 51.0, -110.0),  BlockPos(-86.0, 51.0, -110.0),
        BlockPos(-86.0, 51.0, -109.0),  BlockPos(-84.0, 51.0, -109.0),  BlockPos(-84.0, 51.0, -107.0),
        BlockPos(-90.0, 51.0, -107.0),  BlockPos(-90.0, 51.0, -108.0),  BlockPos(-93.0, 51.0, -108.0),
        BlockPos(-93.0, 51.0, -110.0),  BlockPos(-90.0, 51.0, -110.0),  BlockPos(-90.0, 51.0, -112.0),
        BlockPos(-92.0, 51.0, -112.0),  BlockPos(-92.0, 51.0, -114.0),  BlockPos(-90.0, 51.0, -114.0),
        BlockPos(-90.0, 51.0, -116.0),  BlockPos(-94.0, 51.0, -116.0),  BlockPos(-94.0, 51.0, -114.0),
        BlockPos(-96.0, 51.0, -114.0),  BlockPos(-96.0, 51.0, -116.0),  BlockPos(-98.0, 51.0, -116.0),
        BlockPos(-98.0, 51.0, -112.0),  BlockPos(-96.0, 51.0, -112.0),  BlockPos(-96.0, 51.0, -111.0),
        BlockPos(-95.0, 51.0, -111.0),  BlockPos(-95.0, 51.0, -108.0),  BlockPos(-98.0, 51.0, -108.0),
        BlockPos(-98.0, 51.0, -110.0),  BlockPos(-100.0, 51.0, -110.0), BlockPos(-100.0, 51.0, -108.0),
        BlockPos(-102.0, 51.0, -108.0), BlockPos(-102.0, 51.0, -112.0), BlockPos(-100.0, 51.0, -112.0),
        BlockPos(-100.0, 51.0, -116.0), BlockPos(-102.0, 51.0, -116.0), BlockPos(-102.0, 51.0, -114.0),
        BlockPos(-104.0, 51.0, -114.0), BlockPos(-104.0, 51.0, -112.0), BlockPos(-105.0, 51.0, -112.0)
    )

    fun onMouse() = false

    fun onScreen() = if (isActive) {
        reset()
        mc.player?.resetInput()
    } else null

    fun onRenderWorld(ctx: WorldRenderContext) {
        if (!isActive) ctx.drawFilledBox(points[0].aabb, Colour.GREEN, depth = true)
    }

    fun onMessage(text: String) {
        if (isActive && text.noControlCodes == "EEK! THE LAVA OOFED YOU!") {
            modMessage("&cStopping!")
            reset()
        }
    }

    fun onTick(player: LocalPlayer) {
        if (player.at(points.last())) {
//            modMessage("&aCompleted!")
            reset()
        }
        if (player.at(points[0])) {
            if (++waitingTicks >= 5) start(player)
        } else {
            waitingTicks = 0
        }
    }


    private fun start(player: LocalPlayer) {
        isActive = true
        current = 1
        waitingTicks = 0

        player.yaw = 90f
        player.pitch = 90f

        modMessage("&aStarting!")
        player.moveTo(points)
    }

    private fun reset() {
        isActive = false
        waitingTicks = 0
        current = 0
    }
}