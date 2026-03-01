package quoi.api.autoroutes.actions

import net.minecraft.client.player.LocalPlayer
import quoi.api.colour.Colour
import quoi.config.TypeName

@TypeName("start")
class StartAction : RingAction {

    override val colour: Colour
        get() = Colour.GREEN

    override suspend fun execute(player: LocalPlayer) {
        // nottin
    }
}