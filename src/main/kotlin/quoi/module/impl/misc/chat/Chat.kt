package quoi.module.impl.misc.chat

import quoi.module.Module
import quoi.module.impl.misc.chat.impl.*

object Chat : Module(
    "Chat",
    desc = "Various chat related tweaks."
) {

    @Suppress("unused")
    private val features = setOf(
        ChatBypass,
        ChatPeek,
        CompactChat,
        CopyChat,
        AutoDialogue
    )
}