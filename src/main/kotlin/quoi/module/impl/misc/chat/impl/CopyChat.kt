package quoi.module.impl.misc.chat.impl

import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import quoi.api.events.GuiEvent
import quoi.api.events.core.on
import quoi.api.input.CatKeyboard
import quoi.api.input.CatKeys
import quoi.mixins.accessors.ChatComponentAccessor
import quoi.module.impl.misc.chat.Chat
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.ChatUtils
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.getMessageLineIdx
import quoi.utils.toChatLineMX
import quoi.utils.toChatLineMY
import quoi.utils.visibleMessages

object CopyChat : ToggleableGroup(Chat, "Copy chat", desc = "Copies chat on mouse click.") {

    private val copyKey by keybind("Copy key", CatKeys.MOUSE_RIGHT).includingOnly(CatKeys.MOUSE_RIGHT, CatKeys.MOUSE_LEFT, *CatKeyboard.modifierCodes)
    private val copyCodesKey by keybind("Copy with codes key", CatKeys.KEY_NONE).includingOnly(CatKeys.MOUSE_RIGHT, CatKeys.MOUSE_LEFT, *CatKeyboard.modifierCodes)

    init {
        on<GuiEvent.Click> {
            if (!state || screen !is ChatScreen) return@on
            if (mc.gui.chat.visibleMessages.isEmpty()) return@on

            val isCopyBtn = button == copyKey.key + 100 && copyKey.isModifierDown()
            val isCodeBtn = button == copyCodesKey.key + 100 && copyCodesKey.isModifierDown()
            if (!isCopyBtn && !isCodeBtn) return@on
            cancel()

            val dx = mc.gui.chat.toChatLineMX(mx)
            val dy = mc.gui.chat.toChatLineMY(my)
            val idx = mc.gui.chat.getMessageLineIdx(dx, dy)
            if (idx !in mc.gui.chat.visibleMessages.indices) return@on
            if (idx == 0 && dy !in 0.0..1.0 || dx >= ChatComponent.getWidth(mc.options.chatWidth().get()).plus(10)) return@on

            val fullText = mc.gui.chat.getFullText(idx)?.string ?: return@on
            val finalText = if (isCodeBtn) fullText else fullText.noControlCodes
            mc.keyboardHandler.clipboard = finalText
            ChatUtils.modMessage(
                "&aCopied message to clipboard",
                chatStyle = Style.EMPTY.withHoverEvent(HoverEvent.ShowText(Component.literal(finalText)))
            )
        }
    }

    private fun ChatComponent.getFullText(idx: Int): Component? {
        val visible = (this as ChatComponentAccessor).visibleMessages ?: return null
        if (idx !in visible.indices) return null

        var fullIndex = -1
        for (i in visible.indices) {
            if (visible[i].endOfEntry) fullIndex++
            if (i == idx) break
        }

        return messages.getOrNull(fullIndex)?.content
    }
}