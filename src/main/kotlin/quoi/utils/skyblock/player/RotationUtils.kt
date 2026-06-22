package quoi.utils.skyblock.player

import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth.wrapDegrees
import quoi.QuoiMod.mc
import quoi.annotations.Init
import quoi.api.animations.Animation
import quoi.api.events.MouseEvent
import quoi.api.events.RenderEvent
import quoi.api.events.RotationUpdateEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.api.input.MutableInput
import quoi.api.world.Direction
import quoi.utils.player
import quoi.utils.rad
import kotlin.math.cos
import kotlin.math.sin

@Init
object RotationUtils : EventListener { // todo cleanup

    private var rotationTask: (LocalPlayer.() -> Boolean)? = null

    @JvmStatic
    var serverDirection: Direction? = null
    private var targetDirection: Direction? = null

    private var adjustMovement: Boolean = true

    init {
        on<TickEvent.Start>(Priority.HIGHEST) {
            RotationUpdateEvent().post()
            if (targetDirection != null) {
                val curr = serverDirection ?: Direction(player.yRot, player.xRot)
                serverDirection = targetDirection!!.normalise(curr)
            } else {
                serverDirection = null
            }
        }

        on<RenderEvent.World> {
            while (rotationTask != null) {
                val current = rotationTask!!
                if (player.current()) {
                    if (rotationTask === current) {
                        rotationTask = null
                        break
                    }
                } else break
            }
        }

        on<MouseEvent.Move> {
            if (rotationTask != null) cancel()
        }

        on<WorldEvent.Change> {
            rotationTask = null
            targetDirection = null
            serverDirection = null
        }
    }

    fun rotationTask(task: (LocalPlayer.() -> Boolean)?): Boolean {
        val player = mc.player ?: return false
        rotationTask = task
        return if (task != null) player.task() else true
    }

    fun cancelRotationTask() {
        rotationTask = null
    }

    @JvmStatic
    fun adjustInputFromDirection(input: MutableInput) {
        if (!adjustMovement) return
        val dir = serverDirection ?: return

        val z = (if (input.forward) 1f else 0f) - (if (input.backward) 1f else 0f)
        val x = (if (input.left) 1f else 0f) - (if (input.right) 1f else 0f)

        if (z == 0f && x == 0f) return

        val rad = (player.yRot - dir.yaw).rad

        val cos = cos(rad)
        val sin = sin(rad)

        val nx = x * cos - z * sin
        val nz = z * cos + x * sin

        input.forward = nz > 0.1f
        input.backward = nz < -0.1f
        input.left = nx > 0.1f
        input.right = nx < -0.1f
    }

    var LocalPlayer.yaw
        get() = wrapDegrees(this.yRot)
        set(v) {
            this.yRot = v
            this.yHeadRot = v
        }

    var LocalPlayer.pitch
        get() = this.xRot
        set(v) {
            this.xRot = v
        }

    fun LocalPlayer.rotateSilently(dir: Direction, adjustInput: Boolean = true) {
        targetDirection = dir
        adjustMovement = adjustInput
    }

    fun LocalPlayer.rotateSilently(yaw: Number = this.yaw, pitch: Number = this.pitch, adjustInput: Boolean = true) =
        rotateSilently(Direction(yaw.toFloat(), pitch.toFloat()), adjustInput)

    /**
     * Resets silent rotations to normal
     */
    fun LocalPlayer.resetRotation() {
        targetDirection = null
        serverDirection = null
    }

    fun LocalPlayer.rotate(yaw: Number = this.yaw, pitch: Number = this.pitch) {
        this.yaw = yaw.toFloat()
        this.pitch = pitch.toFloat()
    }

    fun LocalPlayer.rotate(dir: Direction) = this.rotate(dir.yaw, dir.pitch)

    fun LocalPlayer.rotateSmoothly(
        yaw: Float,
        pitch: Float,
        duration: Float,
        style: Animation.Style = Animation.Style.EaseOutQuint,
        onFinish: (() -> Unit)? = null
    ) {
        var initialised = false
        var startYaw = 0f
        var startPitch = 0f
        var deltaYaw = 0f
        var deltaPitch = 0f
        lateinit var anim: Animation

        rotationTask {
            if (!initialised) {
                startYaw = this.yaw
                startPitch = this.pitch
                deltaYaw = wrapDegrees(yaw - startYaw)
                deltaPitch = pitch - startPitch
                anim = Animation(duration, style).onFinish { onFinish?.invoke() }
                initialised = true
            }

            val progress = anim.get()

            this.rotate(
                startYaw + (deltaYaw * progress),
                startPitch + (deltaPitch * progress)
            )

            anim.finished
        }
    }

    fun LocalPlayer.rotateSmoothly(
        dir: Direction,
        duration: Float,
        style: Animation.Style = Animation.Style.EaseOutQuint,
        onFinish: (() -> Unit)? = null
    ) = this.rotateSmoothly(dir.yaw, dir.pitch, duration, style, onFinish)
}