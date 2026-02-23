package quoi.module.impl.misc

import quoi.api.events.ChatEvent
import quoi.module.Module
import quoi.utils.ChatUtils
import quoi.utils.StringUtils.noControlCodes
import net.minecraft.network.chat.ClickEvent

object AutoDialogue : Module(
    "Auto Dialogue",
    desc = "Automatically continues dialogues with NPCs."
) {
    init {
        on<ChatEvent.Receive> {
            if (!message.noControlCodes.startsWith("Select an option: ") || message.noControlCodes.contains("[BARBARIANS] [MAGES]")) return@on
            val command = (text.siblings.getOrNull(0)?.style?.clickEvent as? ClickEvent.RunCommand)?.command ?: return@on
            ChatUtils.command(command)
        }
    }
}