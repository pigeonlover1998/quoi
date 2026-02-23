package quoi.module.impl.dungeon

import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.dsl.*
import quoi.api.colour.Colour
import quoi.api.colour.colour
import quoi.api.events.ChatEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.Location.inSkyblock
import quoi.api.skyblock.dungeon.Dungeon.getMageCooldownMultiplier
import quoi.api.skyblock.dungeon.Dungeon.inBoss
import quoi.api.skyblock.dungeon.Dungeon.inDungeons
import quoi.module.Module
import quoi.module.settings.AlwaysActive
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.NumberSetting
import quoi.utils.StringUtils.capitaliseFirst
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.ui.hud.TextHud
import quoi.utils.ui.hud.setting
import quoi.utils.ui.rendering.NVGRenderer.minecraftFont
import quoi.utils.ui.textPair

@AlwaysActive
object InvincibilityTimer : Module(
    "Invincibility Timer",
    desc = "Slightly Better"
) {
    private val dungeonOnly by BooleanSetting("Dungeons only", desc = "Active in dungeons only.")
    private val bossOnly by BooleanSetting("Boss only", desc = "Active in boss room only.")
//    private val serverTicks by BooleanSetting("Use server ticks", desc = "Uses server ticks instead of real time.")
    private val mageReduction by BooleanSetting("Mage reduction", desc = "Accounts for mage cooldown reduction.")
    private val cataLevel by NumberSetting("Catacombs level", 0, 0, 50, desc = "Catacombs level for Bonzo's mask ability.")

    var phoenixActive = false
    private var mask = Mask.NONE

    private val hud by TextHud("Invincibility timer", Colour.PINK, toggleable = false) {
        visibleIf { this@InvincibilityTimer.enabled && inSkyblock && (!bossOnly || inBoss) && (!dungeonOnly || inDungeons || bossOnly) }
        column {
            InvincibilityType.entries.forEach { type ->
                val (col, time) = type.getTime()
                row(gap = 1.px) {
                    text(
                        string = "◼",
                        font = minecraftFont,
                        size = 18.px,
                        colour = colour { if (type.shouldDot()) colour.rgb else Colour.TRANSPARENT.rgb },
                        pos = at(y = Centre - 2.px)
                    )
                    textPair(
                        string = "${type.displayName}:",
                        supplier = { time() },
                        labelColour = colour,
                        valueColour = col(),
                        shadow = shadow
                    )
                }
            }
        }
    }.withSettings(::dungeonOnly, ::bossOnly, ::mageReduction, ::cataLevel).setting()

    init {
        on<ChatEvent.Packet> {
            val msg = message.noControlCodes
            InvincibilityType.entries.firstOrNull { type -> msg.matches(type.regex) }?.proc()

            when {
                "You summoned your" in msg || "Autopet equipped your" in msg -> phoenixActive = msg.contains("Phoenix")
                "You despawned your Phoenix!" in msg -> phoenixActive = false
            }
        }

        on<TickEvent.Server> {
            InvincibilityType.entries.forEach { it.tick() }
        }

        on<WorldEvent.Change> {
            InvincibilityType.entries.forEach { it.reset() }
            phoenixActive = false
        }

        on<TickEvent.Start> {
            val name = player.inventory.getItem(39).displayName.string
            mask = when {
                name?.contains("Bonzo") == true -> Mask.BONZO
                name?.contains("Spirit") == true -> Mask.SPIRIT
                else -> Mask.NONE
            }
        }
    }

    enum class InvincibilityType(
        val regex: Regex,
        val cooldownTime: Int
    ) {
        BONZO(Regex("^Your (?:. )?Bonzo's Mask saved your life!$"), 3600),
        SPIRIT(Regex("^Second Wind Activated! Your Spirit Mask saved your life!$"), 600),
        PHOENIX(Regex("^Your Phoenix Pet saved you from certain death!$"), 1200);

        val displayName = name.capitaliseFirst()

        var currentCooldown = 0
            private set

        fun proc(customCooldown: Int = cooldownTime) {
            val multiplier = if (mageReduction) getMageCooldownMultiplier() else 1.0
            currentCooldown = when (this) {
                SPIRIT -> (customCooldown * multiplier).toInt()
                BONZO -> ((customCooldown - cataLevel * 72) * multiplier).toInt()
                else -> customCooldown
            }
        }

        fun tick() {
            currentCooldown = (currentCooldown - 1).coerceAtLeast(0)
        }

        fun reset() {
            currentCooldown = 0
        }

        fun getTime(): Pair<() -> Colour, () -> String> {
            val highlight = h@ { // idt it works correctly rn
                if (!phoenixActive) return@h false
                when (this) {
                    BONZO -> mask == Mask.BONZO && PHOENIX.currentCooldown <= 0
                    SPIRIT -> mask == Mask.SPIRIT && PHOENIX.currentCooldown <= 0
                    PHOENIX -> (mask == Mask.SPIRIT && SPIRIT.currentCooldown <= 0)
                            || (mask == Mask.BONZO && BONZO.currentCooldown <= 0)
                }
            }
            val colour = { if (currentCooldown <= 0) Colour.MINECRAFT_GREEN else Colour.MINECRAFT_RED }
            val time = { if (currentCooldown <= 0) "✔" else "%.1f".format(currentCooldown / 20.0) }

            return { if (highlight()) Colour.MINECRAFT_RED else colour() } to time
        }

        fun shouldDot(): Boolean = when (this) {
            BONZO -> mask == Mask.BONZO
            SPIRIT -> mask == Mask.SPIRIT
            PHOENIX -> phoenixActive
        }
    }

    private enum class Mask {
        BONZO, SPIRIT, NONE;
    }
}