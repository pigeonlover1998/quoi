package quoi.module.impl.dungeon.autop3.rings

import net.minecraft.client.KeyMapping
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Input
import quoi.api.colour.Colour
import quoi.api.input.MutableInput
import quoi.config.TypeName
import quoi.module.impl.dungeon.autop3.MovementPredictor

@TypeName("stop")
class StopAction : P3Action {
    override val colour get() = Colour.RED
    @Transient
    override val priority = 110
    
    override fun isStop() = true
    
    override suspend fun execute(player: LocalPlayer) {
        KeyMapping.releaseAll()
    }
    
    override fun execute() = false
    
    override fun tick(player: LocalPlayer, clientInput: Input, input: MutableInput): Boolean {
        val velocity = player.deltaMovement
        val speedSq = velocity.horizontalDistanceSqr()
        if (speedSq < 0.0001) return true
        
        val yaw = Math.toRadians(player.yRot.toDouble()).toFloat()
        
        val fwdX = -Mth.sin(yaw)
        val fwdZ = Mth.cos(yaw)
        val rightX = Mth.cos(yaw)
        val rightZ = Mth.sin(yaw)
        
        val fwdDot = velocity.x * fwdX + velocity.z * fwdZ
        val rightDot = velocity.x * rightX + velocity.z * rightZ
        
        val accel = player.speed * 0.98
        val baseNextSq = MovementPredictor.squaredAfterTick(fwdDot, rightDot, 0.0, 0.0)
        
        val pressFwd = fwdDot < -0.01 && MovementPredictor.squaredAfterTick(fwdDot, rightDot, accel, 0.0) < baseNextSq
        val pressBack = fwdDot > 0.01 && MovementPredictor.squaredAfterTick(fwdDot, rightDot, -accel, 0.0) < baseNextSq
        val pressLeft = rightDot > 0.01 && MovementPredictor.squaredAfterTick(fwdDot, rightDot, 0.0, -accel) < baseNextSq
        val pressRight = rightDot < -0.01 && MovementPredictor.squaredAfterTick(fwdDot, rightDot, 0.0, accel) < baseNextSq
        
        input.forward = pressFwd
        input.backward = pressBack
        input.left = pressLeft
        input.right = pressRight
        return true
    }
    
    override fun feedbackMessage() = "Stopping"
}
