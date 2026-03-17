package quoi.module.impl.dungeon

import quoi.api.events.DungeonEvent
import quoi.api.events.MouseEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Dungeon.allTeammatesNoSelf
import quoi.api.skyblock.dungeon.DungeonClass
import quoi.api.skyblock.dungeon.P3Section
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.skyblock.player.LeapManager

// Kyleen (maybe)
object AutoLeap : Module(
    "Auto Leap",
    desc = "Automatically leaps to predefined targets.",
    area = Island.Dungeon
) {
    private val fastLeap by switch("Fast leap", desc = "Leaps to a set player on infinileap left click.")
    private val fastDelay by slider("Delay", 250L, 100L, 500L, 50L).childOf(::fastLeap) // to not pull bko
    private val autoLeap by switch("Auto leap", desc = "Automatically leaps when a section is finished.")
    private val whenBlown by switch("Only when gate blown", desc = "Only leaps when gate is blown").childOf(::autoLeap)
    private val leapMode by selector("Leap mode", "Name", listOf("Name", "Class"), "Leap mode for the module.")

    private val clearName by textInput("Clear leap", "Clear").visibleIf { leapMode.selected == "Name" }.suggests { allTeammatesNoSelf }
    private val s1Name by textInput("S1 leap", "S1", length = 16).visibleIf { leapMode.selected == "Name" }.suggests { allTeammatesNoSelf }
    private val s2Name by textInput("S2 leap", "S2", length = 16).visibleIf { leapMode.selected == "Name" }.suggests { allTeammatesNoSelf }
    private val s3Name by textInput("S3 leap", "S3", length = 16).visibleIf { leapMode.selected == "Name" }.suggests { allTeammatesNoSelf }
    private val s4Name by textInput("S4 leap", "S4", length = 16).visibleIf { leapMode.selected == "Name" }.suggests { allTeammatesNoSelf }

    private val clearClass by selector("Clear leap", DungeonClass.Unknown).json("Clear leap class").visibleIf { leapMode.selected == "Class" }
    private val s1Class by selector("S1 leap", DungeonClass.Healer).json("S1 leap class").visibleIf { leapMode.selected == "Class" }
    private val s2Class by selector("S2 leap", DungeonClass.Archer).json("S2 leap class").visibleIf { leapMode.selected == "Class" }
    private val s3Class by selector("S3 leap", DungeonClass.Mage).json("S3 leap class").visibleIf { leapMode.selected == "Class" }
    private val s4Class by selector("S4 leap", DungeonClass.Mage).json("S4 leap class").visibleIf { leapMode.selected == "Class" }

    private var lastClick = 0L

    init {
        on<DungeonEvent.SectionComplete> {
            if (!autoLeap || !Dungeon.inP3) return@on
            if (whenBlown) return@on
            handleLeap()
        }

        on<DungeonEvent.SectionComplete.Full> {
            if (!autoLeap || !Dungeon.inP3) return@on
            if (!whenBlown) return@on
            handleLeap()
        }

        on<MouseEvent.Click> {
            if (!fastLeap || button != 0 || state) return@on
//            if (player.mainHandItem.skyblockId != "INFINITE_SPIRIT_LEAP") return@on
            if (!player.mainHandItem.displayName.string.contains("InfiniLeap", true)) return@on

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClick < fastDelay && LeapManager.leapCD < 0) return@on
            handleLeap()
            lastClick = currentTime
        }
    }

    private fun handleLeap() {
        val section = Dungeon.getP3Section()

        val (name, clazz) = if (Dungeon.inClear) {
            clearName to clearClass.selected
        } else {
            when (section) {
                P3Section.S1 -> s1Name to s1Class.selected
                P3Section.S2 -> s2Name to s2Class.selected
                P3Section.S3 -> s3Name to s3Class.selected
                P3Section.S4 -> s4Name to s4Class.selected
                else -> return
            }
        }

        when (leapMode.selected) {
            "Name" -> LeapManager.leap(name)
            "Class" -> LeapManager.leap(clazz)
        }
    }
}