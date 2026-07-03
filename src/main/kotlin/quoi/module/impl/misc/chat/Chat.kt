package quoi.module.impl.misc.chat

import quoi.module.Module
import quoi.module.impl.misc.chat.impl.*

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