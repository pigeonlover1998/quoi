package quoi.module.impl.misc.chat.impl

import net.minecraft.network.chat.ClickEvent
import quoi.api.events.ChatEvent
import quoi.api.events.core.on
import quoi.module.impl.misc.chat.Chat
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.ChatUtils
import quoi.utils.StringUtils.noControlCodes

object AutoDialogue : ToggleableGroup(Chat, "Auto dialogue", desc = "Automatically continues dialogues with NPCs.") {

    init {
        on<ChatEvent.Receive> {
            message.noControlCodes.takeIf { it.startsWith("Select an option: ") && "[BARBARIANS] [MAGES]" !in it }?.let {
                (text.siblings.getOrNull(0)?.style?.clickEvent as? ClickEvent.RunCommand)?.command?.let { ChatUtils.command(it) }
            }
        }
    }
}