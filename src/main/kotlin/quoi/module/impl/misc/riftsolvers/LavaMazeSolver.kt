package quoi.module.impl.misc.riftsolvers

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.aabb
import quoi.utils.addVec
import quoi.utils.blockPos
import quoi.utils.render.drawFilledBox
import quoi.utils.skyblock.player.PlayerUtils.at
import quoi.utils.skyblock.player.RotationUtils.pitch
import quoi.utils.skyblock.player.PlayerUtils.stop
import quoi.utils.skyblock.player.RotationUtils.yaw

object LavaMazeSolver {
    private var isActive = false
    private var current = 0
    private var waitingTicks = 0

    private val points = listOf(
        Vec3(-83.0, 51.0, -112.0),  Vec3(-86.0, 51.0, -112.0),  Vec3(-86.0, 51.0, -115.0),
        Vec3(-88.0, 51.0, -115.0),  Vec3(-88.0, 51.0, -110.0),  Vec3(-86.0, 51.0, -110.0),
        Vec3(-86.0, 51.0, -109.0),  Vec3(-84.0, 51.0, -109.0),  Vec3(-84.0, 51.0, -107.0),
        Vec3(-90.0, 51.0, -107.0),  Vec3(-90.0, 51.0, -108.0),  Vec3(-93.0, 51.0, -108.0),
        Vec3(-93.0, 51.0, -110.0),  Vec3(-90.0, 51.0, -110.0),  Vec3(-90.0, 51.0, -112.0),
        Vec3(-92.0, 51.0, -112.0),  Vec3(-92.0, 51.0, -114.0),  Vec3(-90.0, 51.0, -114.0),
        Vec3(-90.0, 51.0, -116.0),  Vec3(-94.0, 51.0, -116.0),  Vec3(-94.0, 51.0, -114.0),
        Vec3(-96.0, 51.0, -114.0),  Vec3(-96.0, 51.0, -116.0),  Vec3(-98.0, 51.0, -116.0),
        Vec3(-98.0, 51.0, -112.0),  Vec3(-96.0, 51.0, -112.0),  Vec3(-96.0, 51.0, -111.0),
        Vec3(-95.0, 51.0, -111.0),  Vec3(-95.0, 51.0, -108.0),  Vec3(-98.0, 51.0, -108.0),
        Vec3(-98.0, 51.0, -110.0),  Vec3(-100.0, 51.0, -110.0), Vec3(-100.0, 51.0, -108.0),
        Vec3(-102.0, 51.0, -108.0), Vec3(-102.0, 51.0, -112.0), Vec3(-100.0, 51.0, -112.0),
        Vec3(-100.0, 51.0, -116.0), Vec3(-102.0, 51.0, -116.0), Vec3(-102.0, 51.0, -114.0),
        Vec3(-104.0, 51.0, -114.0), Vec3(-104.0, 51.0, -112.0), Vec3(-105.0, 51.0, -112.0)
    )

    fun onMouse() = isActive

    fun onScreen() = if (isActive) reset() else null

    fun onRenderWorld(ctx: WorldRenderContext) {
        if (!isActive) ctx.drawFilledBox(points[0].addVec(x = -1).aabb, Colour.GREEN, depth = true)
    }

    fun onMessage(text: String) {
        if (isActive && text.noControlCodes == "EEK! THE LAVA OOFED YOU!") {
            modMessage("&cStopping!")
            reset()
        }
    }

    fun onTick(player: LocalPlayer) {
        if (!isActive) {
            if (player.at(points[current].addVec(x = -1).blockPos)) {
                if (++waitingTicks >= 5) start()
            } else waitingTicks = 0
            return
        }

        if (!waiting()) {
            next(player)
            return
        }

        if (player.at(points[current].blockPos)) {
            player.stop()
            waitingTicks = 1
        }
    }

    private fun waiting() = waitingTicks <= 0 || --waitingTicks != 0

    private fun next(player: LocalPlayer) {
        if (++current >= points.size) {
            modMessage("&aCompleted!")
            reset()
            return
        }
        move(player, points[current])
    }

    private fun move(player: LocalPlayer, target: Vec3) {
        player.stop()

        val prev = points.getOrElse(current - 1) { points[0] }

        when {
            target.x != prev.x ->
                if (target.x < player.x) mc.options.keyUp.isDown = true
                else mc.options.keyDown.isDown = true

            target.z != prev.z ->
                if (target.z > player.z) mc.options.keyLeft.isDown = true
                else mc.options.keyRight.isDown = true
        }
    }


    private fun start() {
        val player = mc.player ?: return
        isActive = true
        current = 1
        waitingTicks = 0

        player.yaw = 90f
        player.pitch = 90f

        move(player, points[current])

        modMessage("&aStarting!")
    }

    private fun reset() {
        isActive = false
        waitingTicks = 0
        current = 0
        mc.player?.stop()
    }
}