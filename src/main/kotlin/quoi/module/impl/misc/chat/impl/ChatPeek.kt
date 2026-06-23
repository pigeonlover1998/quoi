package quoi.module.impl.misc.chat.impl

import net.minecraft.client.gui.components.ChatComponent
import quoi.api.input.CatKeyboard
import quoi.api.input.CatKeys
import quoi.module.impl.misc.chat.Chat
import quoi.module.settings.group.ToggleableGroup

object ChatPeek : ToggleableGroup(Chat, "Chat peek", desc = "Peeks chat on a button press.") {
    private val peekKey by keybind("Peek key", CatKeys.KEY_Z)
        .onRelease {
            if (running) scroll(-Int.MAX_VALUE)
        }

    @JvmStatic
    fun isDown(): Boolean {
        return running && this.peekKey.isDown()
    }

    @JvmStatic
    fun displayMode(mode: ChatComponent.DisplayMode): ChatComponent.DisplayMode {
        return if (isDown()) ChatComponent.DisplayMode.FOREGROUND else mode
    }

    @JvmStatic
    fun scroll(amount: Int) {
        mc.gui.chat.scrollChat(if (CatKeyboard.Modifier.isShiftDown) amount else amount * 7)
    }
}