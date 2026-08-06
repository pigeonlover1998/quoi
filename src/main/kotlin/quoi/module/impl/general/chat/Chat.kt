package quoi.module.impl.general.chat

import quoi.module.Module
import quoi.module.impl.general.chat.impl.*

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