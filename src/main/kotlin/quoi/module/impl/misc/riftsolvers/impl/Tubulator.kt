package quoi.module.impl.misc.riftsolvers.impl

import net.minecraft.world.phys.Vec3
import quoi.QuoiMod
import quoi.api.colour.Colour
import quoi.api.events.GuiEvent
import quoi.api.events.MouseEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.module.impl.misc.riftsolvers.MirrorverseSolvers
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.ChatUtils.modMessage
import quoi.utils.aabb
import quoi.utils.addVec
import quoi.utils.blockPos
import quoi.utils.getDirection
import quoi.utils.render.drawFilledBox
import quoi.utils.skyblock.player.MovementUtils.stop
import quoi.utils.skyblock.player.PlayerUtils.at
import quoi.utils.skyblock.player.PlayerUtils.eyePosition
import quoi.utils.skyblock.player.RotationUtils.rotate
import kotlin.math.abs
import kotlin.math.hypot

object Tubulator : ToggleableGroup(MirrorverseSolvers, "Tubulator") {
    private var active = false
    private var current = 0
    private var waitingTicks = 0

    init {
        on<TickEvent.End> {
            if (!active) {
                if (player.at(points[0].addVec(y = -1.0).blockPos)) {
                    if (++waitingTicks >= 5) start()
                } else waitingTicks = 0
                return@on
            }

            val prev = points.getOrElse(current - 1) { points[0] }
            if (player.y < prev.y - 1.0) {
                modMessage("&cStopping")
                reset()
                return@on
            }

            if (!waiting()) return@on next()

            if (waitingTicks <= 0) {
                val targetPos = points[current]

                if (hasLanded(targetPos)) {
                    player.stop()
                    QuoiMod.mc.options.keyJump.isDown = false
                    QuoiMod.mc.options.keyShift.isDown = false
                    waitingTicks = 5
                } else {
                    move(targetPos)
                }
            }
        }

        on<RenderEvent.World> {
            ctx.drawFilledBox(points[0].addVec(y = -1.0).aabb, Colour.GREEN, depth = true)
        }

        on<GuiEvent.Open> {
            if (active) reset()
        }

        on<MouseEvent.Move> {
            if (active) cancel()
        }
    }

    private fun move(target: Vec3) {
        val target = target.add(0.5, 0.0, 0.5)
        val prev = points.getOrElse(current - 1) { points[0] }.add(0.5, 0.0, 0.5)

        val dir = getDirection(player.eyePosition(), target)
        player.rotate(yaw = dir.yaw)

        val dist = hypot(player.x - prev.x, player.z - prev.z)
        val distNext = hypot(target.x - player.x, target.z - player.z)
        val totalDist = hypot(target.x - prev.x, target.z - prev.z)

        val edge = dist >= 0.7 && distNext > totalDist / 2
        QuoiMod.mc.options.keyJump.isDown = player.onGround() && edge
        QuoiMod.mc.options.keyShift.isDown = distNext < 0.6 && !edge

        QuoiMod.mc.options.keyUp.isDown = true
        QuoiMod.mc.options.keySprint.isDown = true
    }

    private fun waiting() = waitingTicks <= 0 || --waitingTicks != 0

    private fun next() {
        if (++current >= points.size) {
            modMessage("&aCompleted!")
            reset()
            return
        }
        move(points[current])
    }

    private fun hasLanded(pos: Vec3): Boolean {
        if (!player.onGround()) return false
        val pos = pos.add(0.5, 0.0, 0.5)
        val dx = abs(player.x - pos.x)
        val dz = abs(player.z - pos.z)
        val dy = abs(player.y - pos.y)

        return hypot(dx, dz) <= 0.03 && dy <= 1.5
    }

    private fun start() {
        active = true
        current = 0
        waitingTicks = 0
        modMessage("&aStarting!")
    }

    private fun reset() {
        active = false
        waitingTicks = 0
        current = 0
        player.stop()
        mc.options.keyShift.isDown = false
        mc.options.keyJump.isDown = false
    }

    private val points = listOf(
        Vec3(-302.0, 1.0, -107.0), Vec3(-304.0, 3.0, -107.0), Vec3(-306.0, 5.0, -103.0), Vec3(-308.0, 7.0, -105.0),
        Vec3(-304.0, 9.0, -109.0), Vec3(-300.0, 11.0, -111.0), Vec3(-304.0, 13.0, -107.0), Vec3(-308.0, 15.0, -103.0),
        Vec3(-306.0, 17.0, -107.0), Vec3(-302.0, 19.0, -111.0), Vec3(-300.0, 21.0, -107.0), Vec3(-304.0, 23.0, -111.0),
        Vec3(-306.0, 25.0, -109.0), Vec3(-302.0, 27.0, -111.0), Vec3(-300.0, 29.0, -107.0), Vec3(-304.0, 31.0, -103.0),
        Vec3(-306.0, 33.0, -105.0), Vec3(-302.0, 35.0, -107.0), Vec3(-300.0, 37.0, -109.0), Vec3(-302.0, 39.0, -105.0),
        Vec3(-304.0, 41.0, -107.0), Vec3(-306.0, 43.0, -111.0), Vec3(-302.0, 45.0, -107.0), Vec3(-300.0, 47.0, -103.0),
        Vec3(-304.0, 49.0, -107.0), Vec3(-308.0, 51.0, -105.0), Vec3(-304.0, 53.0, -109.0), Vec3(-306.0, 55.0, -111.0)
    )
}