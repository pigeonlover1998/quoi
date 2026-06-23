package quoi.module.impl.misc.chat.impl

import quoi.api.events.ChatEvent
import quoi.api.events.core.on
import quoi.api.skyblock.Location.onHypixel
import quoi.module.impl.misc.chat.Chat
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.ChatUtils.commandAny
import quoi.utils.ChatUtils.say

object ChatBypass : ToggleableGroup(Chat, "Chat bypass", desc = "Bypasses chat filters on servers.") {

    private val mode by selector("Mode", BypassMode.Cyrillic, desc = "Bypass mode.")

    private var bypass = false

    init {
        on<ChatEvent.Sent> {
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
                        if (content.isNotEmpty()) append(" ${apply(content)}")
                    }

                    cancel()
                    bypass = true
                    commandAny(t)
                }
            } else {
                cancel()
                bypass = true
                say(apply(message))
            }
        }
    }

    val socialCommands = setOf("pc", "ac", "gc", "cc", "r", "msg", "w", "m", "message", "whisper", "tell", "pm") // maybe there are more

    private fun apply(str: String) = mode.selected.apply(str)

    @Suppress("unused")
    private enum class BypassMode {
        Cyrillic {
            private val map = mapOf(
                'a' to 'а', 'A' to 'А', 'e' to 'е', 'E' to 'Е',
                'o' to 'о', 'O' to 'О', 'c' to 'с', 'C' to 'С',
                'p' to 'р', 'P' to 'Р', 'x' to 'х', 'X' to 'Х',
                'y' to 'у', 'Y' to 'У'
            )
            override fun apply(str: String) = str.map { map[it] ?: it }.joinToString("")
        },
        Wide {
            private val map by lazy {
                val normal = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
                val notNormal =
                    "ａｂｃｄｅｆｇｈｉｊｋｌｍｎｏｐｑｒｓｔｕｖｗｘｙｚ" +
                    "ＡＢＣＤＥＦＧＨＩＪＫＬＭＮＯＰＱＲＳＴＵＶＷＸＹＺ" +
                    "０１２３４５６７８９"
                normal.zip(notNormal).toMap()
            }
            override fun apply(str: String): String {
                val s = if (onHypixel) str.lowercase() else str
                return s.map { map[it] ?: it }.joinToString("")
            }
        },
        Dots {
            override fun apply(str: String): String = buildString {
                for (i in str.indices) {
                    append(str[i])
                    if (i < str.length - 1 && str[i] != ' ' && str[i + 1] != ' ') {
                        append('.')
                    }
                }
            }
        },
        SmallCaps {
            private val hMap = mapOf(
                'b' to 'ʙ', 'g' to 'ɢ', 'h' to 'ʜ', 'j' to 'ᴊ', 'q' to 'ǫ', 'z' to 'ᴢ', 'x' to 'x'
            )
            private val map by lazy {
                "abcdefghijklmnopqrstuvwxyz".zip("ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ").toMap()
            }

            override fun apply(str: String): String {
                val m = if (onHypixel) hMap else map
                return str.lowercase().map { m[it] ?: it }.joinToString("")
            }
        };

        abstract fun apply(str: String): String
    }
}