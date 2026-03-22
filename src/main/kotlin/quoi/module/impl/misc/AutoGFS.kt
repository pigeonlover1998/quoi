package quoi.module.impl.misc

import quoi.api.events.TickEvent
import quoi.api.skyblock.Location
import quoi.api.skyblock.SkyblockPlayer
import quoi.api.skyblock.dungeon.Dungeon
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.skyblock.player.PlayerUtils

object AutoGFS : Module( // untested
    "Auto GFS",
    desc = "Automatically refills certain items from your sacks."
) {
    private val itemsDropdown by text("Items to refill")
    private val pearls by switch("Pearls").childOf(::itemsDropdown)
    private val booms by switch("Super booms").childOf(::itemsDropdown)
    private val jerries by switch("Inflatable Jerries").childOf(::itemsDropdown)
    private val leaps by switch("Spirit Leaps").childOf(::itemsDropdown)

    private val mode by selector("Mode", "Amount", arrayListOf("Amount", "Time"))
    private val amount by slider("Amount", 50, 5, 95, 5, unit = "%").childOf(::mode) { it.selected == "Amount" }
    private val time by slider("Time", 5, 1, 60, 1, unit = "s").childOf(::mode) { it.selected == "Time" }

    private val dungeonsOnly by switch("Dungeons only", desc = "Only refill items when in dungeons.")

    private var tickCount = 0
    init {
        on<TickEvent.End> {
            if (dungeonsOnly && !Dungeon.inDungeons) return@on
            if (Dungeon.isDead || !Location.inSkyblock || mc.screen != null) return@on
            if (!SkyblockPlayer.canUseCommands) return@on.also { tickCount = 10000 }

            if (++tickCount < when (mode.selected) {
                    "Amount" -> 20
                    "Time" -> time * 20
                    else -> Int.MAX_VALUE
                }
            ) return@on
            tickCount = 0

            when (mode.selected) {
                "Amount" -> RefillItem.entries.forEach { if (it.shouldRefill()) it.refill() }
                "Time" -> RefillItem.entries.forEach { if (it.enabled) it.refill() }
            }
        }
    }

    private fun isBelowPercentage(n: Int, max: Int) = n < (amount / 100.0) * max

    private enum class RefillItem(
        val maxStack: Int,
        val itemId: String,
        val sackName: String
    ) {
        PEARL(16, "ENDER_PEARL", "ender_pearl"),
        BOOM(64, "SUPERBOOM_TNT", "superboom_tnt"),
        JERRY(64, "INFLATABLE_JERRY", "inflatable_jerry"),
        LEAP(16, "SPIRIT_LEAP", "spirit_leap");

        val enabled get() = when(this) {
            PEARL -> pearls
            BOOM -> booms
            JERRY -> jerries
            LEAP -> leaps
        }

        fun shouldRefill(): Boolean {
            return enabled && isBelowPercentage(PlayerUtils.getItemsAmount(itemId), maxStack)
        }

        fun refill() {
            PlayerUtils.fillItemFromSack(itemId, maxStack, sackName)
        }
    }
}