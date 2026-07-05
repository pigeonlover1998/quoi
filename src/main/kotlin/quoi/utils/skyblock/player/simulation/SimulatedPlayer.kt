package quoi.utils.skyblock.player.simulation

import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.util.Mth
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.VoxelShape
import quoi.api.input.MutableInput
import quoi.utils.WorldUtils.state
import quoi.utils.blockPos
import quoi.utils.player
import quoi.utils.rad
import kotlin.math.abs
import kotlin.math.max

/**
 * modified LiquidBounce (GPL-3.0)
 * copyright (c) 2015-2026 CCBlueX
 * original: https://github.com/CCBlueX/LiquidBounce/blob/nextgen/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/SimulatedPlayer.kt
 */
class SimulatedPlayer(
    private val player: LocalPlayer,
    var input: MutableInput,
    var pos: Vec3,
    var deltaMovement: Vec3,
    var yRot: Float,
    var isSprinting: Boolean,
    var fallDistance: Double,
    private var jumpTriggerTime: Int,
    private var jumping: Boolean,
    var onGround: Boolean,
    var horizontalCollision: Boolean,
    private var verticalCollision: Boolean
)  {
    private var simulatedTicks: Int = 0

    fun tick() {
        if (pos.y <= -70) return

        if (this.jumpTriggerTime > 0) {
            this.jumpTriggerTime--
        }

        this.jumping = input.jump

        val movement = this.deltaMovement

        var motionX = movement.x
        var motionY = movement.y
        var motionZ = movement.z

        if (abs(movement.x) < 0.003) motionX = 0.0
        if (abs(movement.y) < 0.003) motionY = 0.0
        if (abs(movement.z) < 0.003) motionZ = 0.0

        this.deltaMovement = Vec3(motionX, motionY, motionZ)

        if (this.jumping && this.onGround && jumpTriggerTime == 0) {
            this.jumpFromGround()
            jumpTriggerTime = 10
        }

        var forward = 0.0f
        var strafe = 0.0f

        if (input.forward) forward += 1.0f
        if (input.backward) forward -= 1.0f
        if (input.left) strafe += 1.0f
        if (input.right) strafe -= 1.0f

        if (input.shift) {
            forward *= 0.3f
            strafe *= 0.3f
        }

        this.travel(Vec3(strafe * 0.98, 0.0, forward * 0.98))
        simulatedTicks++
    }

    private fun travel(movementInput: Vec3) {
        val gravity = 0.08

        val blockPos = getBlockPosBelowThatAffectsMyMovement()
        val p = blockPos.state.block.friction
        val friction = if (onGround) p * 0.91f else 0.91f

        val speed = if (onGround) {
            player.getAttributeValue(Attributes.MOVEMENT_SPEED).toFloat() * (0.21600002f / (p * p * p))
        } else {
            if (this.input.sprint) (0.02f + 0.005999999865889549).toFloat()
            else 0.02f
        }

        val inputVec = Entity.getInputVector(movementInput, speed, this.yRot)
        this.deltaMovement = this.deltaMovement.add(inputVec)

        this.move(this.deltaMovement)

        var verticalMovement = this.deltaMovement.y

        if (!this.player.isNoGravity) {
            verticalMovement -= gravity
        }

        this.deltaMovement = Vec3(
            this.deltaMovement.x * friction,
            verticalMovement * 0.9800000190734863,
            this.deltaMovement.z * friction
        )
    }

    private fun move(input: Vec3) {
        val adjustedMovement = this.adjustMovementForCollisions(input)

        if (adjustedMovement.lengthSqr() > 1.0E-7) {
            this.pos = this.pos.add(adjustedMovement)
        }

        val xCollision = !Mth.equal(input.x, adjustedMovement.x)
        val zCollision = !Mth.equal(input.z, adjustedMovement.z)

        this.horizontalCollision = xCollision || zCollision
        this.verticalCollision = input.y != adjustedMovement.y

        onGround = verticalCollision && input.y < 0.0

        if (onGround) {
            fallDistance = 0.0
        } else if (input.y < 0) {
            fallDistance -= input.y.toFloat()
        }

        val vec3d2 = this.deltaMovement
        if (horizontalCollision || verticalCollision) {
            this.deltaMovement = Vec3(
                if (xCollision) 0.0 else vec3d2.x,
                if (onGround) 0.0 else vec3d2.y,
                if (zCollision) 0.0 else vec3d2.z
            )
        }
    }

    private fun adjustMovementForCollisions(movement: Vec3): Vec3 {
        val collisionBox = player.dimensions.makeBoundingBox(this.pos)

        val entityCollisionList = emptyList<VoxelShape>()

        val adjustedMovement = if (movement.lengthSqr() == 0.0) {
            movement
        } else {
            Entity.collideBoundingBox(
                this.player,
                movement,
                collisionBox,
                this.player.level(),
                entityCollisionList
            )
        }

        val collidedX = movement.x != adjustedMovement.x
        val collidedY = movement.y != adjustedMovement.y
        val collidedZ = movement.z != adjustedMovement.z

        val onGroundOrFalling = onGround || collidedY && movement.y < 0.0

        if (this.player.maxUpStep() > 0.0f && onGroundOrFalling && (collidedX || collidedZ)) {
            var steppedMovement = Entity.collideBoundingBox(
                this.player,
                Vec3(movement.x, this.player.maxUpStep().toDouble(), movement.z),
                collisionBox,
                this.player.level(),
                entityCollisionList
            )
            val stepUpMovement = Entity.collideBoundingBox(
                this.player,
                Vec3(0.0, this.player.maxUpStep().toDouble(), 0.0),
                collisionBox.expandTowards(movement.x, 0.0, movement.z),
                this.player.level(),
                entityCollisionList
            )
            val stepDownMovement = Entity.collideBoundingBox(
                this.player,
                Vec3(movement.x, 0.0, movement.z),
                collisionBox.move(stepUpMovement),
                this.player.level(),
                entityCollisionList
            ).add(stepUpMovement)

            if (stepUpMovement.y < this.player.maxUpStep().toDouble() && stepDownMovement.horizontalDistanceSqr() > steppedMovement.horizontalDistanceSqr()) {
                steppedMovement = stepDownMovement
            }

            if (steppedMovement.horizontalDistanceSqr() > adjustedMovement.horizontalDistanceSqr()) {
                return steppedMovement.add(
                    Entity.collideBoundingBox(
                        this.player,
                        Vec3(0.0, -steppedMovement.y + movement.y, 0.0),
                        collisionBox.move(steppedMovement),
                        this.player.level(),
                        entityCollisionList
                    )
                )
            }
        }
        return adjustedMovement
    }

    private fun jumpFromGround() {
        val jumpPower = getJumpPower()
        this.deltaMovement = Vec3(this.deltaMovement.x, max(jumpPower, this.deltaMovement.y), this.deltaMovement.z)

        if (isSprinting) {
            val yawRadians = this.yRot.rad.toDouble()

            this.deltaMovement = this.deltaMovement.add(
                (-Mth.sin(yawRadians) * 0.2f).toDouble(),
                0.0,
                (Mth.cos(yawRadians) * 0.2f).toDouble()
            )
        }
    }

    private fun getBlockPosBelowThatAffectsMyMovement() =
        BlockPos.containing(this.pos.x, this.pos.y - 0.5000001, this.pos.z)

    private fun getJumpPower(): Double =
        this.player.getAttributeValue(Attributes.JUMP_STRENGTH) * this.getJumpVelocityMultiplier() + this.getJumpBoostPower()

    private fun getJumpBoostPower(): Double =
        if (hasStatusEffect(MobEffects.JUMP_BOOST)) {
            0.1 * (getStatusEffect(MobEffects.JUMP_BOOST)!!.amplifier + 1.0)
        } else {
            0.0
        }

    private fun getJumpVelocityMultiplier(): Float {
        val f = pos.blockPos.state.block?.jumpFactor ?: 0f
        val g = getBlockPosBelowThatAffectsMyMovement().state.block?.jumpFactor ?: 0f

        return if (f.toDouble() == 1.0) g else f
    }

    @Suppress("SameParameterValue")
    private fun hasStatusEffect(effect: Holder<MobEffect>): Boolean {
        val instance = player.getEffect(effect) ?: return false
        return instance.duration == -1 || instance.duration >= this.simulatedTicks
    }

    @Suppress("SameParameterValue")
    private fun getStatusEffect(effect: Holder<MobEffect>): MobEffectInstance? {
        val instance = player.getEffect(effect) ?: return null
        val duration = if (instance.duration == -1) Int.MAX_VALUE else instance.duration
        if (duration < this.simulatedTicks) {
            return null
        }
        return instance
    }

    companion object {
        @JvmStatic
        fun fromPlayer(input: MutableInput): SimulatedPlayer {
            return SimulatedPlayer(
                player,
                input,
                player.position(),
                player.deltaMovement,
                player.yRot,
                player.isSprinting,
                player.fallDistance,
                player.noJumpDelay,
                player.jumping,
                player.onGround(),
                player.horizontalCollision,
                player.verticalCollision
            )
        }
    }
}