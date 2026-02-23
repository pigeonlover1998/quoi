package quoi.utils

import quoi.QuoiMod.mc
import quoi.mixininterfaces.IChatComponent
import quoi.mixininterfaces.IGuiMessage
import quoi.mixins.accessors.ChatComponentAccessor
import quoi.module.impl.render.ClickGui.bracketsColour
import quoi.module.impl.render.ClickGui.prefixColour
import quoi.module.impl.render.ClickGui.prefixText
import quoi.utils.StringUtils.noControlCodes
import com.mojang.authlib.GameProfile
import net.fabricmc.fabric.impl.command.client.ClientCommandInternals
import net.minecraft.client.GuiMessage
import net.minecraft.client.GuiMessageTag
import net.minecraft.network.chat.*

object ChatUtils {
    fun prefix(text: String = prefixText): Component
        = literal("[").withColor(bracketsColour.rgb)
            .append(literal(text).withColor(prefixColour.rgb))
            .append("]").withColor(bracketsColour.rgb)
    val chatHudAccessor get() = mc.gui?.chat as ChatComponentAccessor
    val chatGui get() = mc.gui?.chat
    val chatHud get() = mc.gui?.chat as IChatComponent

    @Suppress("CAST_NEVER_SUCCEEDS")
    val GuiMessage.asI: IGuiMessage? get() = this as? IGuiMessage
    val IGuiMessage.id: Int get() = this.`quoi$getId`()
    fun IGuiMessage.setId(id: Int) = this.`quoi$setId`(id)
    val IGuiMessage.text: String get() = this.`quoi$getText`()
    val IGuiMessage.sender: GameProfile get() = this.`quoi$getSender`()
    fun IGuiMessage.setSender(profile: GameProfile) = this.`quoi$setSender`(profile)
    fun IChatComponent.add(text: Component, id: Int) = this.`quoi$add`(text, id)

    fun literal(string: String): MutableComponent {
        return Component.literal(string.replace("&", "§"))
    }

    fun removeLines(id: Int, text: String): Boolean {
        return removeLines { it.asI?.id == id || it.content?.string?.noControlCodes == text }
    }

    fun removeLines(id: Int): Boolean {
        return removeLines { it.asI?.id == id }
    }

    fun removeLines(cb: (GuiMessage) -> Boolean): Boolean {
        var removedLine = false
        val messageList = chatHudAccessor.messages?.listIterator() ?: return false

        while (messageList.hasNext()) {
            val msg = messageList.next()
            if (!cb(msg)) continue

            messageList.remove()
            removedLine = true
        }

        if (removedLine) chatHudAccessor.invokeRefreshTrimmedMessages()

        return removedLine
    }

    fun editLines(id: Int, replaceWith: Component): Boolean {
        return editLines({ it.asI?.id == id }, replaceWith)
    }

    fun editLines(cb: (GuiMessage) -> Boolean, replaceWith: Component): Boolean {
        var editedLine = false
        val indicator =
            if (mc.isSingleplayer) GuiMessageTag.systemSinglePlayer()
            else GuiMessageTag.system()
        val messageList = chatHudAccessor.messages?.listIterator() ?: return false

        while (messageList.hasNext()) {
            val msg = messageList.next()
            if (!cb(msg)) continue

            editedLine = true
            messageList.remove()

            val line = GuiMessage(msg.addedTime, replaceWith, null, indicator)
            line.asI?.setId(msg.asI!!.id)
            messageList.add(line)
        }

        if (editedLine) chatHudAccessor.invokeRefreshTrimmedMessages()

        return editedLine
    }

    fun command(command: String, client: Boolean = false) {
        val cmd = command.removePrefix("/")
        if (!client) mc.player?.connection?.sendCommand(cmd)
        else ClientCommandInternals.executeCommand(cmd)
    }

    fun commandAny(command: String) {
        if (!ClientCommandInternals.executeCommand(command)) command(command)
    }

    fun say(message: String) = mc.connection?.sendChat(message)

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
            id?.let { chatHud.add(text, it) } ?: chatGui?.addMessage(text)
        }
    }
}