package quoi.module.impl.dungeon.autop3

import net.minecraft.world.phys.Vec2
import kotlin.math.*

object MovementPredictor {
    fun getDisplacementFromInput(walkSpeed: Double, sneaking: Boolean): Double {
        var speed = walkSpeed
        if (sneaking) speed *= 0.3
        val movementTicks = getInputMovementTicks(speed)
        return 0.098 * speed * (1.0 - 0.546000082.pow(movementTicks)) / (1.0 - 0.546000082)
    }
    
    fun getDisplacementVector(velocity: Vec2): Vec2 {
        val magnitude = sqrt(velocity.x * velocity.x + velocity.y * velocity.y)
        if (magnitude < 1e-6) return Vec2.ZERO
        
        val displacement = getDisplacementMagnitude(velocity)
        val scale = displacement / magnitude
        
        return Vec2(velocity.x * scale, velocity.y * scale)
    }
    
    fun squaredAfterTick(fwd: Double, right: Double, dFwd: Double, dRight: Double): Double {
        val nf = (fwd + dFwd) * 0.546000082
        val nr = (right + dRight) * 0.546000082
        return nf * nf + nr * nr
    }
    
    private fun getDisplacementMagnitude(velocity: Vec2): Float {
        val magnitude = sqrt(velocity.x * velocity.x + velocity.y * velocity.y)
        val movementTicks = ceil(ln(0.003 / magnitude) / ln(0.546000082)).toInt()
        
        if (movementTicks <= 0) return magnitude
        return (magnitude * (1.0 - 0.546000082.pow(movementTicks)) / (1.0 - 0.546000082)).toFloat()
    }
    
    private fun getInputMovementTicks(velocity: Double): Int {
        return ceil(ln(0.003 / (0.098 * velocity)) / ln(0.546000082)).toInt()
    }
}
