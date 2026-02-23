package quoi.api.autoroutes.actions

import quoi.config.TypeName
import quoi.utils.ChatUtils
import net.minecraft.client.player.LocalPlayer

@TypeName("command")
class CommandAction(val command: String = "") : RingAction {
    override suspend fun execute(player: LocalPlayer) {
        ChatUtils.commandAny(command)
    }
}