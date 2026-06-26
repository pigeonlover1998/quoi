package quoi.module.impl.misc.chat.impl

import quoi.module.impl.misc.chat.Chat
import quoi.module.settings.group.ToggleableGroup

// from: https://github.com/jcnlk/quoi/blob/26.1.x/src/main/kotlin/quoi/module/impl/misc/chat/impl/InfiniteChatLimit.kt
object NoChatLimit : ToggleableGroup(Chat, "No chat limit", desc = "Keeps all chat messages instead of trimming chat history at 100 messages.") {
    @JvmStatic
    fun keepChat(): Boolean {
        return running
    }
}