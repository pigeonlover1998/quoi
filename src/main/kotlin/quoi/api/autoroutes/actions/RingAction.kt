package quoi.api.autoroutes.actions

import quoi.config.TypeNamed
import net.minecraft.client.player.LocalPlayer

sealed interface RingAction : TypeNamed {
    suspend fun execute(player: LocalPlayer)
}