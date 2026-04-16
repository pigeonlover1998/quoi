package quoi.module.impl.misc

import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import quoi.api.events.ChatEvent
import quoi.api.events.GuiEvent
import quoi.api.events.core.Priority
import quoi.api.input.CatKeyboard
import quoi.api.input.CatKeyboard.Modifier.isShiftDown
import quoi.api.input.CatKeys
import quoi.mixins.accessors.ChatComponentAccessor
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.ChatUtils
import quoi.utils.ChatUtils.chatGui
import quoi.utils.ChatUtils.literal
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.add
import quoi.utils.chatWidth
import quoi.utils.getMessageLineIdx
import quoi.utils.scrolledLines
import quoi.utils.toChatLineMX
import quoi.utils.toChatLineMY
import quoi.utils.visibleMessages

object Chat : Module(
    "Chat",
    desc = "Various chat related tweaks."
) {

    private val chatBypass by switch("Chat bypass", desc = "Bypasses chat filters on servers.")
    private val bypassMode by selector("Mode", "Wide", arrayListOf("Wide", "Cyrillic"), desc = "Bypass mode.").childOf(::chatBypass)

    private val chatPeek by switch("Chat peek", desc = "Peeks chat on a button press.")
    private val peekKey by keybind("Peek key", CatKeys.KEY_Z).childOf(::chatPeek)

    private val compactChat by switch("Compact chat", desc = "Compacts message duplicates.")
    private val compactChatTime by slider("Compact timer", 60, 5, 120, desc = "Time until compact chat no longer compacts the same message.", unit = "s").childOf(::compactChat)

    private val copyChat by switch("Copy chat", desc = "Copies chat on right click (hold ctrl to copy with colour codes).")
    private val copyChatKey by keybind("Copy key", CatKeys.MOUSE_RIGHT).includingOnly(CatKeys.MOUSE_RIGHT, CatKeys.MOUSE_LEFT, *CatKeyboard.modifierCodes).childOf(::copyChat)
    private val copyChatCodesKey by keybind("Copy with codes key", CatKeys.KEY_NONE).includingOnly(CatKeys.MOUSE_RIGHT, CatKeys.MOUSE_LEFT, *CatKeyboard.modifierCodes).childOf(::copyChat)

    private val autoDialogue by switch("Auto dialogue", desc = "Automatically continues dialogues with NPCs.")

    init {
        on<ChatEvent.Sent> {
            if (!chatBypass) return@on
            if (bypass) {
                bypass = false
                return@on
            }

            if (isCommand) {
                socialCommands.firstOrNull { message.equals(it, true) || message.startsWith("$it ", true) }?.let { cmd ->
                    val isPm = socialCommands.drop(4).any { message.startsWith(it) }
                    val text = message.removePrefix(cmd).trimStart()
                    val shit = if (isPm) text.split(" ").first() else ""
                    val content = text.removePrefix(shit).trimStart()

                    val t = buildString {
                        append(cmd)
                        if (shit.isNotEmpty()) append(" $shit")
                        if (content.isNotEmpty()) append(" ${stupid(content)}")
                    }

                    this.cancel()
                    bypass = true
                    ChatUtils.commandAny(t)
                }
            } else {
                this.cancel()
                bypass = true
                ChatUtils.say(stupid(message))
            }
        }

        on<ChatEvent.Receive> (Priority.LOWEST) {
            if (autoDialogue) message.noControlCodes.takeIf { it.startsWith("Select an option: ") && "[BARBARIANS] [MAGES]" !in it }?.let {
                (text.siblings.getOrNull(0)?.style?.clickEvent as? ClickEvent.RunCommand)?.command?.let { ChatUtils.command(it) }
            }

            if (!compactChat || id != 0) return@on // don't compact messages with ids

            val msg = this.message.trim()
            if (msg.isEmpty()) return@on

            if (msg.all { it == '-' || it == '=' || it == '▬' }) return@on

            val data = chatList[msg]
            val lastTime = data?.second
            val id = msg.hashCode()

            if (lastTime != null && System.currentTimeMillis() - lastTime < compactChatTime * 1000) {
                val count = data.first + 1
                this.cancel()

                scheduleTask {
                    val scrollBefore = chatGui.scrolledLines // without this scroll resets every time message gets compacted. visual bug: scroll bar changes colour for a split second. I can't be asked fixing it
                    ChatUtils.removeLines(id, msg)
                    chatGui.add(text.copy().append(literal(" &7($count)")), id)
                    chatList[msg] = Pair(count, System.currentTimeMillis())
                    chatGui.scrolledLines = scrollBefore
                }

                return@on
            }
            chatList[msg] = Pair(1, System.currentTimeMillis())
        }

        on<GuiEvent.Click> {
            if (!state || !copyChat || screen !is ChatScreen) return@on
            if (chatGui.visibleMessages.isEmpty()) return@on

            val isCopyBtn = button == copyChatKey.key + 100 && copyChatKey.isModifierDown()
            val isCodeBtn = button == copyChatCodesKey.key + 100 && copyChatCodesKey.isModifierDown()
            if (!isCopyBtn && !isCodeBtn) return@on
            cancel()

            val dx = chatGui.toChatLineMX(mx)
            val dy = chatGui.toChatLineMY(my)
            val idx = chatGui.getMessageLineIdx(dx, dy)
            if (idx !in chatGui.visibleMessages.indices) return@on
            if (idx == 0 && dy !in 0.0..1.0 || dx >= chatGui.chatWidth + 10) return@on

            val fullText = chatGui.getFullText(idx)?.string ?: return@on
            val finalText = if (isCodeBtn) fullText else fullText.noControlCodes
            mc.keyboardHandler.clipboard = finalText
            modMessage(
                "&aCopied message to clipboard",
                chatStyle = Style.EMPTY.withHoverEvent(HoverEvent.ShowText(Component.literal(finalText)))
            )
        }
    }

    override fun onKeybind() {  }

    // chat bypass
    private var bypass = false
    val socialCommands = setOf("pc", "ac", "gc", "cc", "r", "msg", "w", "m", "message", "whisper", "tell", "pm") // maybe there are more

    private val wideMap: Map<Char, Char> by lazy {
        val normal = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val notNormal = "ａｂｃｄｅｆｇｈｉｊｋｌｍｎｏｐｑｒｓｔｕｖｗｘｙｚ" +
                "ＡＢＣＤＥＦＧＨＩＪＫＬＭＮＯＰＱＲＳＴＵＶＷＸＹＺ" +
                "０１２３４５６７８９"
        normal.zip(notNormal).toMap()
    }

    private val cyrillicMap = mapOf(
        'a' to 'а', 'A' to 'А',
        'e' to 'е', 'E' to 'Е',
        'o' to 'о', 'O' to 'О',
        'c' to 'с', 'C' to 'С',
        'p' to 'р', 'P' to 'Р',
        'x' to 'х', 'X' to 'Х',
        'y' to 'у', 'Y' to 'У'
    )

    private fun stupid(str: String): String {
        val map = when (bypassMode.selected) {
            "Wide" -> wideMap
            "Cyrillic" -> cyrillicMap
            else -> return ""
        }

        val sb = StringBuilder(str.length)
        for (ch in str) sb.append(map[ch] ?: ch)
        return sb.toString()
    }

    // chat peek
    fun isDown(): Boolean {
        return this.enabled && chatPeek && this.peekKey.isDown()
    }

    fun scroll(amount: Int) {
        chatGui?.scrollChat(if (isShiftDown) amount else amount * 7)
    }

    // compact chat
    val chatList = mutableMapOf<String, Pair<Int, Long>>()

    // copy chat
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
