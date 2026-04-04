package quoi.utils.skyblock.player

import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth.wrapDegrees
import quoi.QuoiMod.mc
import quoi.annotations.Init
import quoi.api.animations.Animation
import quoi.api.events.RenderEvent
import quoi.api.events.core.EventBus.on
import quoi.utils.Direction

@Init
object RotationUtils {

    init {
        on<RenderEvent.World> {
            val task = rotationTask ?: return@on
            val player = mc.player ?: return@on

            val anim = task.anim
            val progress = anim.get()

            player.rotate(
                task.startYaw + (task.deltaYaw * progress),
                task.startPitch + (task.deltaPitch * progress)
            )

            if (anim.finished && rotationTask?.anim === anim) {
                rotationTask = null
            }
        }
    }

    private var rotationTask: RotationTask? = null

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
        val startYaw = this.yaw
        val startPitch = this.pitch

        val deltaYaw = wrapDegrees(yaw - startYaw)
        val deltaPitch = pitch - startPitch

        rotationTask = RotationTask(
            startYaw,
            startPitch,
            deltaYaw,
            deltaPitch,
            Animation(duration, style).onFinish { onFinish?.invoke() }
        )
    }

    fun LocalPlayer.rotateSmoothly(dir: Direction, duration: Float, style: Animation.Style = Animation.Style.EaseOutQuint, onFinish: (() -> Unit)? = null) =
        this.rotateSmoothly(dir.yaw, dir.pitch, duration, style, onFinish)

    private data class RotationTask(
        val startYaw: Float,
        val startPitch: Float,
        val deltaYaw: Float,
        val deltaPitch: Float,
        val anim: Animation
    )
}