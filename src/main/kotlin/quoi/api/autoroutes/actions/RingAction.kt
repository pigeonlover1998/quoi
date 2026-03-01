package quoi.api.autoroutes.actions

import quoi.config.TypeNamed
import net.minecraft.client.player.LocalPlayer
import quoi.api.colour.Colour

sealed interface RingAction : TypeNamed {
    suspend fun execute(player: LocalPlayer)
    val colour: Colour
}