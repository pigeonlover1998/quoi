package quoi.module.impl.general.chat

import quoi.module.Module
import quoi.module.impl.general.chat.impl.AutoDialogue
import quoi.module.impl.general.chat.impl.ChatBypass
import quoi.module.impl.general.chat.impl.ChatPeek
import quoi.module.impl.general.chat.impl.CompactChat
import quoi.module.impl.general.chat.impl.CopyChat
import quoi.module.impl.general.chat.impl.KeepChatHistory
import quoi.module.impl.general.chat.impl.NoChatLimit

@Suppress("unused_expression")
object Chat : Module(
    "Chat",
    desc = "Various chat related tweaks."
) {
    init {
        ChatBypass
        ChatPeek
        CompactChat
        CopyChat
        NoChatLimit
        KeepChatHistory
        AutoDialogue
    }
}