package quoi.utils.skyblock.player

import net.minecraft.client.KeyMapping
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import quoi.annotations.Init
import quoi.api.events.KeyEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.api.input.MutableInput
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.Shortcuts
import quoi.utils.WorldUtils.airLike
import quoi.utils.WorldUtils.solid
import quoi.utils.WorldUtils.ticksUntilCollision
import quoi.utils.distanceTo2D
import quoi.utils.rad
import quoi.utils.skyblock.player.RotationUtils.yaw
import quoi.utils.skyblock.player.simulation.PlayerSimulation
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Init
object MovementUtils : EventListener, Shortcuts {

    private var movementTask: (LocalPlayer.(MutableInput) -> Boolean)? = null
    private var currentInput: MutableInput? = null

    init {
        on<KeyEvent.Input>(Priority.LOWEST) {
            currentInput = input

            while (movementTask != null) {
                val current = movementTask!!
                if (player.current(input)) {
                    if (movementTask === current) {
                        movementTask = null
                        break
                    }
                } else break
            }
            currentInput = null
        }

        on<WorldEvent.Change> {
            movementTask = null
        }
    }

    fun movementTask(task: (LocalPlayer.(MutableInput) -> Boolean)?): Boolean {
        movementTask = task
        val input = currentInput ?: return false
        return if (task != null) player.task(input) else true
    }

    fun cancelMovementTask() {
        movementTask = null
    }

    fun moving() = movementTask != null

    fun LocalPlayer.fullStop() = movementTask { input ->
        if (!this.isMoving) return@movementTask this.resetInput()

        val s = sin(yaw.rad)
        val c = cos(yaw.rad)

        val vx = deltaMovement.x * -s + deltaMovement.z * c
        val vz = deltaMovement.x * c + deltaMovement.z * s
        val accel = speed * 0.98

        val baseSq = stepVelSq(vx, vz, 0.0, 0.0)
        input.forward  = vx < -0.01 && stepVelSq(vx, vz, accel, 0.0) < baseSq
        input.backward = vx > 0.01  && stepVelSq(vx, vz, -accel, 0.0) < baseSq
        input.right    = vz > 0.01  && stepVelSq(vx, vz, 0.0, -accel) < baseSq
        input.left     = vz < -0.01 && stepVelSq(vx, vz, 0.0, accel) < baseSq

        false
    }

    fun LocalPlayer.resetInput() = movementTask { input ->
        val holding = input.forward || input.backward || input.left || input.right
        if (holding) {
            input.forward = false
            input.backward = false
            input.left = false
            input.right = false
        }
        !holding
    }

    @JvmName("moveTo_")
    fun LocalPlayer.moveTo(path: List<Vec3>, onFinish: (() -> Unit)? = null) = movementTask { // todo recode
        if (path.isEmpty()) {
            onFinish?.invoke()
            return@movementTask true
        }
        var index = 0

        var jumpedGap = false
        var gapTarget: Vec3? = null

        movementTask { input ->
            if (index >= path.size) {
                onFinish?.invoke()
                return@movementTask true
            }

            val speed = deltaMovement.horizontalDistanceSqr()
            val switchDist = if (index == path.size - 2) 0.3 else (0.5 + speed)

            while (index < path.size - 1 && position().distanceTo2D(path[index]) < switchDist) {
                index++
            }

            val target = path[index]
            val isLast = index == path.size - 1

            val slide = slideVec(deltaMovement.x, deltaMovement.z)
            val pPos = position().add(slide.x.toDouble(), 0.0, slide.y.toDouble())
            if (isLast && pPos.distanceTo2D(target) < 0.5) {
                this.resetInput()
                onFinish?.invoke()
                return@movementTask true
            }

            val s = sin(yaw.rad)
            val c = cos(yaw.rad)

            val dx = if (isLast) target.x - pPos.x else target.x - x
            val dz = if (isLast) target.z - pPos.z else target.z - z

            val vx = dx * -s + dz * c
            val vz = dx * c + dz * s

            val angle = atan2(vz, vx)
            val octant = floor(angle / (Math.PI / 4.0) + 0.5).toInt()

            input.forward = false
            input.backward = false
            input.left = false
            input.right = false

            when (octant) {
                0 -> input.forward = true
                1 -> {
                    input.forward = true
                    input.left = true
                }
                2 -> input.left = true
                3 -> {
                    input.backward = true
                    input.left = true
                }
                4, -4 -> input.backward = true
                -3 -> {
                    input.backward = true
                    input.right = true
                }
                -2 -> input.right = true
                -1 -> {
                    input.forward = true
                    input.right = true
                }
            }

            if (onGround()) {
                jumpedGap = false
                gapTarget = null

                val higher = target.y > y + 0.6
                val stuck = horizontalCollision && speed < 0.01

                val clear = blockPosition().above(1).airLike && blockPosition().above(2).airLike

                val gap = abs(target.y - y) < 0.6 && !stuck && run { // todo make it not shit
                    val dx = target.x - x
                    val dz = target.z - z
                    val dist = hypot(dx, dz)

                    if (dist <= 1.1) return@run false

                    val gapBlock = (1..3).firstNotNullOfOrNull { step ->
                        val checkDist = step * 0.6
                        val pos = BlockPos.containing(
                            x + (dx / dist) * checkDist,
                            y - 0.1,
                            z + (dz / dist) * checkDist
                        )
                        if (!pos.solid && !pos.below().solid) pos else null
                    } ?: return@run false

                    val ticks = ticksUntilCollision(gapBlock)
                    ticks != null && ticks < 1
                }

                input.jump = (higher || stuck || gap) && clear

                if (gap && input.jump) {
                    jumpedGap = true
                    gapTarget = target
                }
            } else if (jumpedGap) {
                // predict where player will actually land. if it's past gapTarget
                // then brake by inverting keys instead of continuing to go forward trhogh the whole jump
                if (gapTarget != null) {
                    val toTarget = Vec3(gapTarget!!.x - x, 0.0, gapTarget!!.z - z)
                    val distToTarget = toTarget.length()

                    if (distToTarget > 1e-4) {
                        val dir = toTarget.scale(1.0 / distToTarget)

                        val landing = PlayerSimulation.simulation.findSnapshot(1..20) { it.onGround }

                        if (landing != null) {
                            val landDist = landing.pos.subtract(Vec3(x, 0.0, z)).dot(dir)
                            val overshoot = landDist - distToTarget

                            if (overshoot > 0.15) {
                                input.invert()
                            }
                        }
                    }
                }
            }

            false
        }
    }
    fun LocalPlayer.moveTo(path: List<BlockPos>, onFinish: (() -> Unit)? = null) = moveTo(path.map { it.center }, onFinish)
    fun LocalPlayer.moveTo(target: Vec3, onFinish: (() -> Unit)? = null) = moveTo(listOf(target), onFinish)
    fun LocalPlayer.moveTo(target: BlockPos, onFinish: (() -> Unit)? = null) = moveTo(listOf(target.center), onFinish)

    val LocalPlayer.isMoving get() = deltaMovement.x != 0.0 || deltaMovement.z != 0.0 || input.hasForwardImpulse()

    fun KeyMapping.hold(ticks: Int) {
        isDown = true
        scheduleTask(ticks) {
            isDown = false
        }
    }

    fun LocalPlayer.stop() {
        mc.options.keyUp.isDown = false
        mc.options.keyDown.isDown = false
        mc.options.keyLeft.isDown = false
        mc.options.keyRight.isDown = false
        mc.options.keyJump.isDown = false
        mc.options.keySprint.isDown = false
        mc.options.keyJump.isDown = false
    }

    private const val DRAG = 0.546000082
    private const val FRICTION = 0.45399991799999995
    private const val STOP_VELOCITY = 0.003
    private const val ACCEL = 0.098

    fun futureVel(ticks: Int, speed: Double) =
        DRAG.pow(ticks) * ACCEL * speed

    fun inputDist(speed: Double, sneak: Boolean): Double {
        val s = if (sneak) speed * 0.3 else speed
        val t = inputTicks(s)
        return (ACCEL * s * (1.0 - DRAG.pow(t))) / FRICTION
    }

    fun stepVelSq(vx: Double, vz: Double, ax: Double, az: Double): Double {
        val nx = (vx + ax) * DRAG
        val nz = (vz + az) * DRAG
        return nx * nx + nz * nz
    }

    fun stopTicks(dx: Float, dz: Float): Int {
        val mag = sqrt(dx * dx + dz * dz).toDouble()
        return if (mag <= STOP_VELOCITY) 0 else ceil(ln(STOP_VELOCITY / mag) / ln(DRAG)).toInt()
    }

    fun slideDist(vel: Vec2): Double {
        val mag = vel.length().toDouble()
        val t = if (mag <= STOP_VELOCITY) 0 else ceil(ln(STOP_VELOCITY / mag) / ln(DRAG)).toInt()
        return if (t <= 0) mag else (mag * (1.0 - DRAG.pow(t))) / FRICTION
    }

    fun slideVec(vel: Vec2): Vec2 {
        val m = vel.length()
        return if (m < 1.0E-6f) Vec2.ZERO else vel.scale((slideDist(vel).toFloat() / m))
    }

    fun slideVec(vx: Number, vz: Number) =
        slideVec(Vec2(vx.toFloat(), vz.toFloat()))

    private fun inputTicks(speed: Double) =
        ceil(ln(STOP_VELOCITY / (ACCEL * speed)) / ln(DRAG)).toInt()
}