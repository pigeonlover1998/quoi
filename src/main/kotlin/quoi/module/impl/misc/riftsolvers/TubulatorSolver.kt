package quoi.module.impl.misc.riftsolvers

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.utils.ChatUtils.modMessage
import quoi.utils.aabb
import quoi.utils.addVec
import quoi.utils.blockPos
import quoi.utils.getDirection
import quoi.utils.render.drawFilledBox
import quoi.utils.skyblock.player.PlayerUtils.at
import quoi.utils.skyblock.player.RotationUtils.rotate
import quoi.utils.skyblock.player.MovementUtils.stop
import kotlin.math.abs
import kotlin.math.hypot

object TubulatorSolver {
    private var isActive = false
    private var current = 0
    private var waitingTicks = 0

    private val points = listOf(
        Vec3(-302.0, 1.0, -107.0), Vec3(-304.0, 3.0, -107.0), Vec3(-306.0, 5.0, -103.0), Vec3(-308.0, 7.0, -105.0),
        Vec3(-304.0, 9.0, -109.0), Vec3(-300.0, 11.0, -111.0), Vec3(-304.0, 13.0, -107.0), Vec3(-308.0, 15.0, -103.0),
        Vec3(-306.0, 17.0, -107.0), Vec3(-302.0, 19.0, -111.0), Vec3(-300.0, 21.0, -107.0), Vec3(-304.0, 23.0, -111.0),
        Vec3(-306.0, 25.0, -109.0), Vec3(-302.0, 27.0, -111.0), Vec3(-300.0, 29.0, -107.0), Vec3(-304.0, 31.0, -103.0),
        Vec3(-306.0, 33.0, -105.0), Vec3(-302.0, 35.0, -107.0), Vec3(-300.0, 37.0, -109.0), Vec3(-302.0, 39.0, -105.0),
        Vec3(-304.0, 41.0, -107.0), Vec3(-306.0, 43.0, -111.0), Vec3(-302.0, 45.0, -107.0), Vec3(-300.0, 47.0, -103.0),
        Vec3(-304.0, 49.0, -107.0), Vec3(-308.0, 51.0, -105.0), Vec3(-304.0, 53.0, -109.0), Vec3(-306.0, 55.0, -111.0)
    )

    fun onMouse() = isActive

    fun onScreen() = if (isActive) reset() else null

    fun onRenderWorld(ctx: WorldRenderContext) {
        ctx.drawFilledBox(points[0].addVec(y = -1.0).aabb, Colour.GREEN, depth = true)
    }

    fun onTick(player: LocalPlayer) {
        if (!isActive) {
            if (player.at(points[0].addVec(y = -1.0).blockPos)) {
                if (++waitingTicks >= 5) start()
            } else waitingTicks = 0
            return
        }

        val prev = points.getOrElse(current - 1) { points[0] }
        if (player.y < prev.y - 1.0) {
            modMessage("&cStopping")
            reset()
            return
        }

        if (!waiting()) {
            next(player)
            return
        }

        if (waitingTicks <= 0) {
            val targetPos = points[current]

            if (hasLanded(player, targetPos)) {
                player.stop()
                mc.options.keyJump.isDown = false
                mc.options.keyShift.isDown = false
                waitingTicks = 5
            } else {
                move(player, targetPos)
            }
        }
    }

    private fun move(player: LocalPlayer, target: Vec3) {
        val target = target.add(0.5, 0.0, 0.5)
        val prev = points.getOrElse(current - 1) { points[0] }.add(0.5, 0.0, 0.5)

        val dir = getDirection(player.eyePosition, target)
        player.rotate(yaw = dir.yaw)

        val dist = hypot(player.x - prev.x, player.z - prev.z)
        val distNext = hypot(target.x - player.x, target.z - player.z)
        val totalDist = hypot(target.x - prev.x, target.z - prev.z)

        val edge = dist >= 0.7 && distNext > totalDist / 2
        mc.options.keyJump.isDown = player.onGround() && edge
        mc.options.keyShift.isDown = distNext < 0.6 && !edge

        mc.options.keyUp.isDown = true
        mc.options.keySprint.isDown = true
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

    private fun hasLanded(player: LocalPlayer, pos: Vec3): Boolean {
        if (!player.onGround()) return false
        val pos = pos.add(0.5, 0.0, 0.5)
        val dx = abs(player.x - pos.x)
        val dz = abs(player.z - pos.z)
        val dy = abs(player.y - pos.y)

        return hypot(dx, dz) <= 0.03 && dy <= 1.5
    }

    private fun start() {
        isActive = true
        current = 0
        waitingTicks = 0
        modMessage("&aStarting!")
    }

    private fun reset() {
        isActive = false
        waitingTicks = 0
        current = 0
        mc.player?.stop()
        mc.options.keyShift.isDown = false
        mc.options.keyJump.isDown = false
    }
}