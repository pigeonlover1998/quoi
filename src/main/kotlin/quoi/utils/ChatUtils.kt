package quoi.utils

import quoi.QuoiMod.mc
import quoi.module.impl.render.clickgui.impl.PrefixSettings.bracketsColour
import quoi.module.impl.render.clickgui.impl.PrefixSettings.prefixColour
import quoi.module.impl.render.clickgui.impl.PrefixSettings.prefixText
import quoi.utils.StringUtils.noControlCodes
import net.fabricmc.fabric.impl.command.client.ClientCommandInternals
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.client.multiplayer.chat.GuiMessageSource
import net.minecraft.client.multiplayer.chat.GuiMessageTag
import net.minecraft.network.chat.*
import net.minecraft.world.entity.player.ChatVisiblity
import quoi.mixininterfaces.IChatComponent
import quoi.mixininterfaces.IGuiMessage
import kotlin.math.floor

object ChatUtils {

    @Suppress("cast_never_succeeds")
    inline var GuiMessage.id: Int
        get() = (this as IGuiMessage).`quoi$getId`()
        set(value) {
            (this as IGuiMessage).`quoi$setId`(value)
        }

    fun ChatComponent.add(text: Component, id: Int) =
        (this as IChatComponent).`quoi$add`(text, id)

    fun ChatComponent.toChatLineMX(x: Double): Double =
        x / scale - 4.0

    fun ChatComponent.toChatLineMY(y: Double): Double =
        (mc.window.guiScaledHeight - y - 40.0) / (scale * lineHeight)

    fun ChatComponent.getMessageLineIdx(chatLineX: Double, chatLineY: Double): Int = run {
        if (!isChatFocused || mc.options.chatVisibility().get() == ChatVisiblity.HIDDEN) return -1
        if (chatLineX < -4.0 || chatLineX > floor(width.toDouble() / scale)) return -1

        val lineCount = minOf(linesPerPage, trimmedMessages.size)
        if (chatLineY < 0.0 || chatLineY >= lineCount) return -1

        val idx = floor(chatLineY + chatScrollbarPos.toDouble()).toInt()
        idx.takeIf { it in trimmedMessages.indices } ?: -1
    }

    fun prefix(text: String = prefixText): Component
        = literal("[").withColor(bracketsColour.rgb)
            .append(literal(text).withColor(prefixColour.rgb))
            .append("]").withColor(bracketsColour.rgb)

    fun literal(string: String): MutableComponent {
        return Component.literal(string.replace("&", "§"))
    }

    fun removeLines(id: Int, text: String): Boolean {
        return removeLines { it.id == id || it.content.string.noControlCodes == text }
    }

    fun removeLines(id: Int): Boolean {
        return removeLines { it.id == id }
    }

    fun removeLines(cb: (GuiMessage) -> Boolean): Boolean {
        var removedLine = false
        val messageList = mc.gui.chat.allMessages.listIterator()

        while (messageList.hasNext()) {
            val msg = messageList.next()
            if (!cb(msg)) continue

            messageList.remove()
            removedLine = true
        }

        if (removedLine) mc.gui.chat.refreshTrimmedMessages()

        return removedLine
    }

    fun editLines(id: Int, replaceWith: Component): Boolean {
        return editLines({ it.id == id }, replaceWith)
    }

    fun editLines(cb: (GuiMessage) -> Boolean, replaceWith: Component): Boolean {
        var editedLine = false
        val indicator =
            if (mc.isSingleplayer) GuiMessageTag.systemSinglePlayer()
            else GuiMessageTag.system()
        val messageList = mc.gui.chat.allMessages.listIterator()

        while (messageList.hasNext()) {
            val msg = messageList.next()
            if (!cb(msg)) continue

            editedLine = true
            messageList.remove()

            val line = GuiMessage(msg.addedTime, replaceWith, null, GuiMessageSource.SYSTEM_CLIENT, indicator)
            line.id = msg.id
            messageList.add(line)
        }

        if (editedLine) mc.gui.chat.refreshTrimmedMessages()

        return editedLine
    }

    fun command(command: String, client: Boolean = false) {
        val cmd = command.removePrefix("/")
        if (!client) player.connection.sendCommand(cmd)
        else ClientCommandInternals.executeCommand(cmd)
    }

    fun commandAny(command: String) {
        if (!ClientCommandInternals.executeCommand(command)) command(command)
    }

    fun say(message: String) = connection.sendChat(message)

    fun button(text: String, command: String, hoverText: String? = null, action: ClickEvent.Action = ClickEvent.Action.RUN_COMMAND): MutableComponent {
        val literal = literal(text)

        val clickEvent = when (action) {
            ClickEvent.Action.RUN_COMMAND -> ClickEvent.RunCommand(command)
            ClickEvent.Action.SUGGEST_COMMAND -> ClickEvent.SuggestCommand(command)
            else -> null
        }

        clickEvent?.let {
            literal.style = Style.EMPTY.withClickEvent(it)
            hoverText?.let {
                literal.style = literal.style.withHoverEvent(HoverEvent.ShowText(literal(hoverText)))
            }
        }

        return literal
    }

    fun modMessage(message: Any?, id: Int? = null, prefix: Any = this.prefix(), chatStyle: Style? = null) {
//        val p = if (prefix.isEmpty()) "" else "$prefix "
        val p: MutableComponent = when (prefix) {
            is Component -> Component.empty().append(prefix).append(" ")
            is String -> literal(if (prefix.isEmpty()) "" else "$prefix ")
            else -> literal("$prefix ")
        }
        val text: Component = when (message) {
            is Component -> p.append(message)
            else -> p.append(literal(message.toString()))
        }.also { chatStyle?.let(it::setStyle) }

        mc.execute {
            id?.let { mc.gui.chat.add(text, it) } ?: mc.gui.chat.addClientSystemMessage(text)
        }
    }
}