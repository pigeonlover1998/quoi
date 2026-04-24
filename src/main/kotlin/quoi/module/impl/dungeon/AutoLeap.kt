package quoi.module.impl.dungeon

import net.minecraft.world.phys.Vec3
import quoi.api.events.ChatEvent
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
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.player.LeapManager

// Kyleen (maybe)
object AutoLeap : Module( // todo clean up
    "Auto Leap",
    desc = "Automatically leaps to predefined targets.",
    area = Island.Dungeon,
    tag = Tag.BETA
) {
    private val fastLeap by switch("Fast leap", desc = "Leaps to a set player on infinileap left click.")
    private val fastDelay by slider("Delay", 250L, 100L, 500L, 50L).childOf(::fastLeap) // to not pull bko
    private val autoLeap by switch("Auto leap", desc = "Automatically leaps when a section is finished.")
    private val whenBlown by switch("Only when gate blown", desc = "Only leaps when gate is blown").childOf(::autoLeap)
    private val leapMode by selector("Leap mode", "Name", listOf("Name", "Class"), "Leap mode for the module.").open()

    private val clearName by textInput("Clear leap", "Clear", length = 16).childOf(::leapMode) { it.index == 0 }.suggests { allTeammatesNoSelf }
    private val s1Name by textInput("S1 leap", "S1", length = 16).childOf(::leapMode) { it.index == 0 }.suggests { allTeammatesNoSelf }
    private val s2Name by textInput("S2 leap", "S2", length = 16).childOf(::leapMode) { it.index == 0 }.suggests { allTeammatesNoSelf }
    private val s3Name by textInput("S3 leap", "S3", length = 16).childOf(::leapMode) { it.index == 0 }.suggests { allTeammatesNoSelf }
    private val s4Name by textInput("S4 leap", "S4", length = 16).childOf(::leapMode) { it.index == 0 }.suggests { allTeammatesNoSelf }

    private val clearClass by selector("Clear leap", DungeonClass.Unknown).json("Clear leap class").childOf(::leapMode) { it.index == 1 }
    private val s1Class by selector("S1 leap", DungeonClass.Healer).json("S1 leap class").childOf(::leapMode) { it.index == 1 }
    private val s2Class by selector("S2 leap", DungeonClass.Archer).json("S2 leap class").childOf(::leapMode) { it.index == 1 }
    private val s3Class by selector("S3 leap", DungeonClass.Mage).json("S3 leap class").childOf(::leapMode) { it.index == 1 }
    private val s4Class by selector("S4 leap", DungeonClass.Mage).json("S4 leap class").childOf(::leapMode) { it.index == 1 }

    private var lastClick = 0L

    private val doNotLeapLocations = listOf(
        Vec3(108.5, 120.0, 94.5) to 1.5, // at ss
        Vec3(58.5, 109.0, 131.5) to 1.5, // at ee2
        Vec3(60.5, 132.0, 140.5) to 1.5, // at ee2 high / levers dev
        Vec3(69.5, 109.0, 122.5) to 1.0, // ee2 safe spot 1
        Vec3(48.5, 109.0, 122.5) to 1.0, // ee2 safe spot 2
        Vec3(2.5, 109.0, 104.5) to 1.5,  // at ee3
        Vec3(18.5, 121.0, 99.5) to 3.0,  // ee3 safe spot
        Vec3(1.5, 120.0, 77.5) to 3.0,   // arrows dev
        Vec3(58.5, 123.0, 122.5) to 0.3, // entering core
        Vec3(54.5, 115.0, 51.5) to 1.5   // at core
    )

    init {
        on<DungeonEvent.SectionComplete> {
            if (!autoLeap || !Dungeon.inP3) return@on
            if (whenBlown) return@on
            handleLeap(completedSection = Dungeon.p3Section)
        }

        on<DungeonEvent.SectionComplete.Full> {
            if (!autoLeap || !Dungeon.inP3) return@on
            if (!whenBlown) return@on
            handleLeap(completedSection = Dungeon.p3Section)
        }

        on<ChatEvent.Packet> {
            if (!autoLeap) return@on
            if (message.noControlCodes == "[BOSS] Storm: I should have known that I stood no chance.") {
                handleLeap(forceS1 = true)
            }
            if (message.noControlCodes == "The Core entrance is opening!") {
                handleLeap(completedSection = P3Section.S4)
            }
        }

        on<MouseEvent.Click> {
            if (!fastLeap || button != 0 || !state) return@on
            if (Dungeon.getP3Section() == P3Section.Unknown && !Dungeon.inClear) return@on
//            if (player.mainHandItem.skyblockId != "INFINITE_SPIRIT_LEAP") return@on
            if (!player.mainHandItem.displayName.string.contains("InfiniLeap", true)) return@on

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClick < fastDelay) return@on
            handleLeap(autoLeap = false)
            lastClick = currentTime
        }
    }

    private fun handleLeap(completedSection: P3Section? = null, forceS1: Boolean = false, autoLeap: Boolean = true) {
        if (autoLeap) {
            if (Dungeon.getP3Section() == P3Section.Unknown) return
            for ((pos, distSqr) in doNotLeapLocations) {
                if (player.distanceToSqr(pos) <= distSqr) {
                    return
                }
            }
        }

        val targetSection = if (forceS1) {
            P3Section.S1
        } else if (completedSection != null && completedSection != P3Section.Unknown) {
            when (completedSection) {
                P3Section.S1 -> P3Section.S2
                P3Section.S2 -> P3Section.S3
                P3Section.S3 -> P3Section.S4
                P3Section.S4 -> P3Section.S4
                else -> return
            }
        } else {
            Dungeon.getP3Section()
        }

        val (name, clazz) = if (Dungeon.inClear) {
            clearName to clearClass.selected
        } else {
            when (targetSection) {
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