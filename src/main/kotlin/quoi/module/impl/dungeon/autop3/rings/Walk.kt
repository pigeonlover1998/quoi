package quoi.module.impl.dungeon.autop3.rings

import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.player.Input
import quoi.api.colour.Colour
import quoi.api.input.MutableInput
import quoi.config.TypeName
import quoi.module.impl.dungeon.autop3.AutoP3
import quoi.QuoiMod.mc

@TypeName("walk")
class WalkAction(val yaw: Float = 0f, val pitch: Float = 0f) : P3Action {
    override val colour get() = Colour.CYAN
    @Transient
    override val priority = 50
    
    override fun shouldStop() = true
    
    override suspend fun execute(player: LocalPlayer) {
    }
    
    override fun execute() = true
    
    override fun tick(player: LocalPlayer, clientInput: Input, input: MutableInput): Boolean {
        if (hasInputPressed(clientInput)) return true
        
        AutoP3.setDesync(true)
        if (AutoP3.strafe45 && !mc.player!!.onGround()) {
            mc.player!!.yRot = yaw - 45
            input.right = true
        } else {
            mc.player!!.yRot = yaw
        }
        
        input.forward = true
        input.sprint = true
        return false
    }
    
    private fun hasInputPressed(input: Input): Boolean {
        return input.forward() || input.backward() || input.left() || input.right() || input.jump()
    }
    
    override fun feedbackMessage() = "Walking"
}
