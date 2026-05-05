package quoi.utils.rotation

import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec2
import quoi.QuoiMod.mc
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

object ClientRotationHandler {
    @JvmStatic
    var clientYaw = Float.NaN
        private set
    
    @JvmStatic
    var clientPitch = Float.NaN
        private set
    
    private var desynced = false
    private val providers = mutableListOf<ClientRotationProvider>()
    
    private var lastRotationDeltaYaw = 0f
    private var forwardRemainder = 0f
    private var strafeRemainder = 0f
    private var allowInputs = true
    
    @JvmStatic
    fun setYaw(yaw: Float) {
        clientYaw = yaw
        mc.player?.yRotO = yaw
    }
    
    @JvmStatic
    fun setPitch(pitch: Float) {
        clientPitch = pitch
        mc.player?.xRotO = pitch
    }
    
    @JvmStatic
    fun registerProvider(provider: ClientRotationProvider) {
        providers.add(provider)
    }
    
    @JvmStatic
    fun tick() {
        if (mc.player == null) return
        
        providers.removeIf { !it.isClientRotationActive() }
        allowInputs = providers.all { it.allowClientKeyInputs() }
        
        val active = providers.any { it.isClientRotationActive() }
        
        if (active && !desynced) {
            if (clientYaw.isNaN()) clientYaw = mc.player!!.yRot
            if (clientPitch.isNaN()) clientPitch = mc.player!!.xRot
        }
        if (!active && desynced) {
            clientYaw = Float.NaN
            clientPitch = Float.NaN
        }
        desynced = active
    }
    
    @JvmStatic
    fun handleTurnPlayer(d: Double, dx: Double, dy: Double, smoothTurnX: SmoothDouble, smoothTurnY: SmoothDouble): Boolean {
        if (!isActive()) return false
        
        val options = mc.options
        val e = options.sensitivity().get() * 0.6 + 0.2
        val f = e * e * e
        val g = f * 8.0
        
        val j: Double
        val k: Double
        
        if (options.smoothCamera) {
            val h = smoothTurnX.getNewDeltaValue(dx * g, d * g)
            val i = smoothTurnY.getNewDeltaValue(dy * g, d * g)
            j = h
            k = i
        } else if (options.cameraType.isFirstPerson && mc.player!!.isScoping) {
            smoothTurnX.reset()
            smoothTurnY.reset()
            j = dx * f
            k = dy * f
        } else {
            smoothTurnX.reset()
            smoothTurnY.reset()
            j = dx * g
            k = dy * g
        }
        
        turn(if (options.invertMouseX().get()) -j else j, if (options.invertMouseY().get()) -k else k)
        return true
    }
    
    private fun turn(d: Double, e: Double) {
        if (clientYaw.isNaN() || clientPitch.isNaN()) return
        
        val f = (e * 0.15).toFloat()
        val g = (d * 0.15).toFloat()
        setPitch(clientPitch + f)
        setYaw(clientYaw + g)
        setPitch(Mth.clamp(clientPitch, -90.0f, 90.0f))
    }
    
    @JvmStatic
    fun isActive() = desynced
    
    @JvmStatic
    fun adjustInputsForRotation(inputs: Input): Input {
        if (!allowInputs) return Input(false, false, false, false, false, false, false)
        if (!desynced || mc.player == null) return inputs
        if (clientYaw.isNaN()) return inputs
        
        val moveVector = constructMovementVector(inputs)
        if (moveVector.x == 0f && moveVector.y == 0f) {
            forwardRemainder = 0f
            strafeRemainder = 0f
            lastRotationDeltaYaw = clientYaw - mc.player!!.yRot
            return inputs
        }
        
        val currentDeltaYaw = clientYaw - mc.player!!.yRot
        val deltaYaw = currentDeltaYaw - lastRotationDeltaYaw
        if (deltaYaw != 0f) {
            val newRemainder = rotateVector(forwardRemainder, strafeRemainder, deltaYaw)
            forwardRemainder = newRemainder.x
            strafeRemainder = newRemainder.y
        }
        
        lastRotationDeltaYaw = currentDeltaYaw
        val rotatedMovementVector = rotateVector(moveVector.x, moveVector.y, currentDeltaYaw)
        val newForward = Mth.clamp(rotatedMovementVector.x - forwardRemainder, -1f, 1f)
        val newStrafe = Mth.clamp(rotatedMovementVector.y - strafeRemainder, -1f, 1f)
        
        val forwardsMovement = round(newForward)
        val strafeMovement = round(newStrafe)
        
        forwardRemainder = forwardsMovement - newForward
        strafeRemainder = strafeMovement - newStrafe
        
        return Input(
            forwardsMovement == 1f,
            forwardsMovement == -1f,
            strafeMovement == 1f,
            strafeMovement == -1f,
            inputs.jump(),
            inputs.shift(),
            inputs.sprint()
        )
    }
    
    @JvmStatic
    fun syncServerRotationToClient() {
        if (mc.player == null) return
        if (clientYaw.isNaN() || clientPitch.isNaN()) return
        mc.player!!.yRot = clientYaw
        mc.player!!.xRot = clientPitch
    }
    
    private fun constructMovementVector(inputs: Input): Vec2 {
        var forward = 0f
        var strafe = 0f
        if (inputs.forward()) forward += 1f
        if (inputs.backward()) forward -= 1f
        if (inputs.left()) strafe += 1f
        if (inputs.right()) strafe -= 1f
        return Vec2(forward, strafe)
    }
    
    private fun rotateVector(x: Float, y: Float, angle: Float): Vec2 {
        val rad = Math.toRadians(angle.toDouble())
        val cos = cos(rad).toFloat()
        val sin = sin(rad).toFloat()
        return Vec2(x * cos - y * sin, x * sin + y * cos)
    }
}
