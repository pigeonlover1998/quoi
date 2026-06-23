package quoi.module.impl.misc.chat.impl

import quoi.api.events.ChatEvent
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.module.impl.misc.chat.Chat
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.ChatUtils
import quoi.utils.ChatUtils.literal
import quoi.utils.Scheduler
import quoi.utils.add
import quoi.utils.scrolledLines

object CompactChat : ToggleableGroup(Chat, "Compact chat", desc = "Compacts message duplicates.") {

    private val compactTime by slider("Compact time", 60, 5, 120, desc = "Time until compact chat no longer compacts the same message.", unit = "s")

    val chatList = mutableMapOf<String, Pair<Int, Long>>()

    init {
        on<ChatEvent.Receive> (Priority.LOWEST) {
            if (id != 0) return@on // don't compact messages with ids

            val msg = this.message.trim()
            if (msg.isEmpty()) return@on

            if (msg.all { it == '-' || it == '=' || it == '▬' }) return@on

            val data = chatList[msg]
            val lastTime = data?.second
            val id = msg.hashCode()

            if (lastTime != null && System.currentTimeMillis() - lastTime < compactTime * 1000) {
                val count = data.first + 1
                this.cancel()

                Scheduler.scheduleTask {
                    val scrollBefore = mc.gui.chat.scrolledLines // without this scroll resets every time message gets compacted. visual bug: scroll bar changes colour for a split second. I can't be asked fixing it
                    ChatUtils.removeLines(id, msg)
                    mc.gui.chat.add(text.copy().append(literal(" &7($count)")), id)
                    chatList[msg] = Pair(count, System.currentTimeMillis())
                    mc.gui.chat.scrolledLines = scrollBefore
                }

                return@on
            }
            chatList[msg] = Pair(1, System.currentTimeMillis())
        }
    }
}