package quoi.module.impl.dungeon.autop3.rings

import net.minecraft.client.KeyMapping
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.input.MutableInput
import quoi.config.TypeName
import quoi.module.impl.dungeon.autop3.AutoP3
import quoi.module.impl.dungeon.autop3.MovementPredictor
import kotlin.math.*

@TypeName("fastalign")
class FastAlignAction : P3Action {
    override val colour get() = Colour.CYAN
    @Transient
    override val priority = 105
    
    override fun isStop() = true
    
    @Transient
    private var yaws: ArrayDeque<Pair<Float, Boolean>>? = null
    
    @Transient
    private var initialized = false
    
    override suspend fun execute(player: LocalPlayer) {
        yaws = null
        initialized = false
        if (!player.onGround()) {
            return
        }
        
        val initialVelocity = player.deltaMovement
        val initialDisplacement = MovementPredictor.getDisplacementVector(Vec2(initialVelocity.x.toFloat(), initialVelocity.z.toFloat()))
        
        val position = player.position()
        val ring = AutoP3.rings.find { ring -> ring.subActions.contains(this) || ring.action == this }
        val boxCenter = ring?.boundingBox()?.center ?: return
        val target = Vec3(boxCenter.x, position.y, boxCenter.z)
        val delta = target.subtract(position.add(initialDisplacement.x.toDouble(), 0.0, initialDisplacement.y.toDouble()))
        val deltaLength = delta.length()
        
        var sneaking = true
        var displacement = MovementPredictor.getDisplacementFromInput(player.speed * 10.0, sneaking)
        
        if (deltaLength < 0.01) {
            yaws = ArrayDeque()
            return
        }
        
        if (deltaLength > 2 * displacement) {
            sneaking = false
            displacement = MovementPredictor.getDisplacementFromInput(player.speed * 10.0, sneaking)
            if (deltaLength > 2 * displacement) {
                return
            }
        }
        
        KeyMapping.releaseAll()
        
        val yaw = atan2(-delta.z, delta.x)
        val theta = acos(deltaLength / (2 * displacement))
        
        yaws = ArrayDeque()
        yaws!!.add((-Math.toDegrees(yaw + theta).toFloat() - 90f) to sneaking)
        yaws!!.add((-Math.toDegrees(yaw - theta).toFloat() - 90f) to sneaking)
    }
    
    override fun execute() = false
    
    override fun tick(player: LocalPlayer, clientInput: Input, input: MutableInput): Boolean {
        val yawQueue = yaws
        if (yawQueue == null) {
            return !player.onGround()
        }
        
        if (yawQueue.isEmpty()) {
            val vel = player.deltaMovement
            if (vel.x == 0.0 && vel.z == 0.0) return true
            if (vel.lengthSqr() > (0.3 * 0.3)) return false
            
            val ring = AutoP3.rings.find { ring -> ring.subActions.contains(this) || ring.action == this }
            val boxCenter = ring?.boundingBox()?.center ?: return true
            val target = Vec3(boxCenter.x, player.position().y, boxCenter.z)
            return player.position().distanceToSqr(target) <= 0.25 * 0.25
        }
        
        val peek = yawQueue.firstOrNull() ?: return true
        if (peek.second && !player.lastSentInput.shift()) {
            input.shift = true
            return false
        }
        
        AutoP3.setDesync(true)
        
        val entry = yawQueue.removeFirst()
        player.yRot = entry.first
        input.shift = entry.second
        input.forward = true
        return false
    }
    
    override fun feedbackMessage() = "Aligning faster"
}
