package quoi.module.impl.misc

import quoi.api.events.TickEvent
import quoi.api.skyblock.Location
import quoi.api.skyblock.dungeon.Dungeon
import quoi.module.Module
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.DropdownSetting
import quoi.module.settings.impl.NumberSetting
import quoi.module.settings.impl.SelectorSetting
import quoi.utils.skyblock.player.PlayerUtils

object AutoGFS : Module( // untested
    "Auto GFS",
    desc = "Automatically refills certain items from your sacks."
) {
    private val itemsDropdown by DropdownSetting("Items to refill").collapsible()
    private val pearls by BooleanSetting("Pearls").withDependency(itemsDropdown)
    private val booms by BooleanSetting("Super booms").withDependency(itemsDropdown)
    private val jerries by BooleanSetting("Inflatable Jerries").withDependency(itemsDropdown)
    private val leaps by BooleanSetting("Spirit Leaps").withDependency(itemsDropdown)

    private val mode by SelectorSetting("Mode", "Amount", arrayListOf("Amount", "Time"))
    private val amount by NumberSetting("Amount", 50, 5, 95, 5, unit = "%").withDependency { mode.selected == "Amount" }
    private val time by NumberSetting("Time", 5, 1, 60, 1, unit = "s").withDependency { mode.selected == "Time" }

    private val dungeonsOnly by BooleanSetting("Dungeons only", desc = "Only refill items when in dungeons.")

    private var tickCount = 0
    init {
        on<TickEvent.End> {
            if (dungeonsOnly && !Dungeon.inDungeons) return@on
            if (Dungeon.isDead || !Location.inSkyblock || mc.screen != null) return@on

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
        val enabled: Boolean,
        val maxStack: Int,
        val itemId: String,
        val sackName: String
    ) {
        PEARL(pearls, 16, "ENDER_PEARL", "ender_pearl"),
        BOOM(booms, 64, "SUPERBOOM_TNT", "superboom_tnt"),
        JERRY(jerries, 64, "INFLATABLE_JERRY", "inflatable_jerry"),
        LEAP(leaps, 16, "SPIRIT_LEAP", "spirit_leap");

        fun shouldRefill(): Boolean {
            return enabled && isBelowPercentage(PlayerUtils.getItemsAmount(itemId), maxStack)
        }

        fun refill() {
            PlayerUtils.fillItemFromSack(itemId, maxStack, sackName)
        }
    }
}