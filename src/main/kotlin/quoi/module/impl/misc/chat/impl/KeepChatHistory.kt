package quoi.module.impl.misc.chat.impl

import quoi.module.impl.misc.chat.Chat
import quoi.module.settings.group.ToggleableGroup

// from: https://github.com/jcnlk/quoi/blob/26.1.x/src/main/kotlin/quoi/module/impl/misc/chat/impl/KeepChatHistory.kt
object KeepChatHistory : ToggleableGroup(Chat, "Keep chat history", desc = "Keeps chat history when vanilla tries to clear it on disconnect.") {
    @JvmStatic
    fun keepsChat(): Boolean {
        return running
    }
}