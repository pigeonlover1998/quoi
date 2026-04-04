package quoi.module.impl.misc.riftsolvers

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.Vec3
import quoi.utils.aabb
import quoi.utils.getDirection
import quoi.utils.render.drawFilledBox
import quoi.utils.skyblock.player.RotationUtils.rotate
import quoi.utils.skyblock.player.PlayerUtils.stop
import kotlin.math.abs
import kotlin.math.hypot

object ParkourSolver { // super schizo but works so I don't care
    private var isActive = false
    private var current = 0
    private var waitingTicks = 0

    private val points = listOf(
        Vec3(-121, 39, -108), Vec3(-125, 39, -108), Vec3(-129, 39, -108), Vec3(-133, 40, -108),
        Vec3(-137, 39, -104), Vec3(-133, 40, -100), Vec3(-137, 41, -96),  Vec3(-141, 42, -100),
        Vec3(-145, 40, -104), Vec3(-145, 40, -108), Vec3(-141, 39, -112), Vec3(-137, 40, -116),
        Vec3(-141, 41, -120), Vec3(-145, 42, -116), Vec3(-149, 43, -112), Vec3(-153, 42, -108),
        Vec3(-157, 41, -104), Vec3(-153, 40, -100), Vec3(-157, 40, -96),  Vec3(-161, 39, -92),
        Vec3(-165, 40, -96),  Vec3(-165, 41, -100), Vec3(-165, 42, -104), Vec3(-165, 43, -108),
        Vec3(-161, 42, -112), Vec3(-157, 39, -116), Vec3(-161, 40, -120), Vec3(-165, 41, -124),
        Vec3(-169, 41, -120), Vec3(-169, 42, -116), Vec3(-173, 43, -112), Vec3(-173, 43, -108),
        Vec3(-177, 40, -104), Vec3(-173, 41, -100), Vec3(-173, 41, -96),  Vec3(-177, 39, -92),
        Vec3(-181, 40, -96),  Vec3(-185, 41, -100), Vec3(-181, 42, -104), Vec3(-185, 41, -108),
        Vec3(-181, 40, -112), Vec3(-185, 41, -116), Vec3(-181, 40, -120), Vec3(-185, 41, -124),
        Vec3(-189, 42, -120), Vec3(-189, 43, -116), Vec3(-189, 43, -112), Vec3(-193, 41, -108),
        Vec3(-193, 42, -104), Vec3(-189, 43, -100), Vec3(-193, 42, -96),  Vec3(-197, 39, -92),
        Vec3(-201, 40, -96),  Vec3(-205, 41, -100), Vec3(-209, 42, -104), Vec3(-205, 43, -108),
        Vec3(-201, 43, -108), Vec3(-197, 43, -112), Vec3(-201, 40, -116), Vec3(-205, 41, -116),
        Vec3(-205, 42, -120), Vec3(-209, 43, -120), Vec3(-213, 41, -116), Vec3(-213, 41, -112),
        Vec3(-217, 42, -108), Vec3(-221, 43, -104), Vec3(-223, 43, -104)
    )

    fun onMouse() = isActive

    fun onScreen() = if (isActive) reset() else null

    fun onRenderWorld(ctx: WorldRenderContext) {
        if (!isActive) ctx.drawFilledBox(points[0].aabb.expandTowards(-1.0, 0.0, -1.0).move(0.0, -1.0, 0.0), Colour.GREEN, depth = true)
    }

    fun onMessage(text: String) {
        if (isActive && text.noControlCodes == "OH NO! THE LAVA OOFED YOU BACK TO THE START!") {
            modMessage("&cStopping!")
            reset()
        }
    }

    fun onTick(player: LocalPlayer) {
        if (!isActive) {
            if (points[0].aabb.expandTowards(-1.0, 0.0, -1.0).intersects(player.boundingBox)) {
                if (++waitingTicks >= 5) start()
            } else waitingTicks = 0
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
                waitingTicks = 5
            } else {
                move(player, targetPos)
            }
        }
    }

    private fun move(player: LocalPlayer, target: Vec3) {
        val prev = points.getOrElse(current - 1) { points[0] }
        val dir = getDirection(player.eyePosition, target)
        player.rotate(yaw = dir.yaw)

        val dist = hypot(player.x - prev.x, player.z - prev.z)
        val distNext = hypot(target.x - player.x, target.z - player.z)
        val totalDist = hypot(target.x - prev.x, target.z - prev.z)

        val edge = dist >= 0.95 && distNext > totalDist / 2
        mc.options.keyJump.isDown = player.onGround() && edge
        mc.options.keyShift.isDown = distNext < 0.7 && !edge

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
        val dx = player.x - pos.x
        val dz = player.z - pos.z
        val dy = abs(player.y - pos.y)
        return hypot(dx, dz) <= 0.03 && dy <= 1.0
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
    }
}