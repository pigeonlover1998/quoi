package quoi.api.autoroutes.actions

import net.minecraft.client.player.LocalPlayer
import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.config.TypeName

@TypeName("unsneak")
class UnSneakAction : RingAction {
    override val colour: Colour
        get() = Colour.MAGENTA

    override suspend fun execute(player: LocalPlayer) {
        mc.options.keyShift.isDown = false
    }
}