package quoi.api.skyblock

import net.minecraft.world.entity.ai.attributes.Attributes
import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.api.events.ChatEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventBus.on
import quoi.api.skyblock.dungeon.Dungeon.getMageCooldownMultiplier
import quoi.module.impl.dungeon.InvincibilityTimer.cataLevel
import quoi.module.impl.dungeon.InvincibilityTimer.mageReduction
import quoi.module.impl.render.ClickGui.currentPet
import quoi.module.impl.render.ClickGui.updateCurrentPet
import quoi.utils.Scheduler.scheduleLoop
import quoi.utils.StringUtils.capitaliseFirst
import quoi.utils.StringUtils.noControlCodes
import kotlin.math.floor

object SkyblockPlayer {
    inline val health: Int
        get() = mc.player?.let { (maxHealth * it.health / it.maxHealth).toInt() } ?: 0
    var maxHealth: Int = 0
        private set
    var absorption: Int = 0
        private set

    var defence: Int = 0
        private set

    var mana: Int = 0
        private set
    var maxMana: Int = 0
        private set

    var overflowMana: Int = 0
        private set
    var stacks: String = ""
        private set
    var salvation: Int = 0
        private set

    var effectiveHealth: Int = 0
        private set
    inline val speed: Int
        get() = floor(
            (mc.player?.getAttribute(Attributes.MOVEMENT_SPEED)?.baseValue?.toFloat() ?: 0f) * 1000f
        ).toInt()

    var manaUsage: String = ""
        private set

    var currentSecrets: Int = -1
        private set
    var maxSecrets: Int = -1
        private set

    var currentMask: Mask = Mask.NONE
        private set

    var currentPet get() = currentPet()
        set(v) { updateCurrentPet(v) }

    val HP_REGEX = Regex("§[c6]([\\d,]+)/([\\d,]+)❤") // §c1389/1390❤ , §62181/1161❤
    val DEF_REGEX = Regex("§a([\\d,]+)§a❈ Defense") // §a593§a❈ Defense
    val MANA_REGEX = Regex("§b([\\d,]+)/([\\d,]+)✎( Mana)?") // §b550/550✎ Mana§r

    val OVERFLOW_REGEX = Regex("§3([\\d,]+)ʬ") // §3100ʬ
    val STACKS_REGEX = Regex("§6([0-9]+[ᝐ⁑Ѫ])") // §610⁑
    val SALVATION_REGEX = Regex("T([1-3])!") // no idea

    val MANA_USAGE_REGEX = Regex("§b-[\\d,]+ Mana \\(§6.+?§b\\)|§c§lNOT ENOUGH MANA") // §b-50 Mana (§6Speed Boost§b) , §c§lNOT ENOUGH MANA
    val SECRETS_REGEX = Regex("\\s*§7(\\d+)/(\\d+) Secrets") // §76/10 Secrets§r

    val SUMMON_REGEX = Regex("You (summoned|despawned) your ([A-Za-z ]+)(?: ✦)?!")
    val AUTOPET_REGEX = Regex("Autopet.*?equipped your.*?\\[Lvl \\d+] (.*?)!.*VIEW RULE")

    fun init() {
        on<ChatEvent.ActionBar> {
//            if (packet !is ClientboundSystemChatPacket || !packet.overlay) return@on
            val message = message.replace(",", "")

            HP_REGEX.find(message)?.destructured?.let { (abs, max) ->
                maxHealth = max.toInt()
                absorption = (abs.toInt() - max.toInt()).coerceAtLeast(0)
                effectiveHealth = maxHealth * (1 + defence / 100)
            }

            defence = DEF_REGEX.find(message)?.groupValues?.get(1)?.toIntOrNull() ?: defence

            MANA_REGEX.find(message)?.destructured?.let { (mana, maxMana) ->
                this@SkyblockPlayer.mana = mana.toInt()
                this@SkyblockPlayer.maxMana = maxMana.toInt()
            }

            overflowMana = OVERFLOW_REGEX.find(message)?.destructured?.component1()?.toInt() ?: 0

            stacks = STACKS_REGEX.find(message)?.destructured?.component1() ?: ""

            salvation = SALVATION_REGEX.find(message)?.destructured?.component1()?.toInt() ?: 0

            manaUsage = MANA_USAGE_REGEX.find(message)?.value ?: ""

            SECRETS_REGEX.find(message)?.destructured?.let { (current, max) ->
                currentSecrets = current.toInt()
                maxSecrets = max.toInt()
            } ?: run {
                currentSecrets = -1
                maxSecrets = -1
            }
        }

        on<ChatEvent.Packet> {
            val msg = message.noControlCodes
            InvincibilityType.entries.firstOrNull { type -> msg.matches(type.regex) }?.proc()

            SUMMON_REGEX.find(msg)?.destructured?.let { (action, name, _) ->
                currentPet =
                    if (action == "summoned")
                        name.trim()
                    else
                        ""
            }
            AUTOPET_REGEX.find(message)?.groupValues?.get(1)?.let { currentPet = it.trim() }
        }

        on<TickEvent.Server> {
            InvincibilityType.entries.forEach { it.tick() }
        }

        on<WorldEvent.Change> {
            InvincibilityType.entries.forEach { it.reset() }
        }

        scheduleLoop {
            val name = mc.player?.inventory?.getItem(39)?.displayName?.string
            currentMask = when {
                name?.contains("Bonzo") == true -> Mask.BONZO
                name?.contains("Spirit") == true -> Mask.SPIRIT
                else -> Mask.NONE
            }
        }
    }

    data class SkyblockPet(val name: String, val level: Int, val texture: String) // todo

    /**
     * modified OdinFabric (BSD 3-Clause)
     * copyright (c) 2025-2026 odtheking
     * original: https://github.com/odtheking/OdinFabric/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/dungeon/InvincibilityTimer.kt
     */
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
                if (!currentPet.contains("phoenix", true)) return@h false
                when (this) {
                    BONZO -> currentMask == Mask.BONZO && PHOENIX.currentCooldown <= 0
                    SPIRIT -> currentMask == Mask.SPIRIT && PHOENIX.currentCooldown <= 0
                    PHOENIX -> (currentMask == Mask.SPIRIT && SPIRIT.currentCooldown <= 0)
                            || (currentMask == Mask.BONZO && BONZO.currentCooldown <= 0)
                }
            }
            val colour = { if (currentCooldown <= 0) Colour.MINECRAFT_GREEN else Colour.MINECRAFT_RED }
            val time = { if (currentCooldown <= 0) "✔" else "%.1f".format(currentCooldown / 20.0) }

            return { if (highlight()) Colour.MINECRAFT_RED else colour() } to time
        }

        fun shouldDot(): Boolean = when (this) {
            BONZO -> currentMask == Mask.BONZO
            SPIRIT -> currentMask == Mask.SPIRIT
            PHOENIX -> currentPet.contains("phoenix", true)
        }
    }

    enum class Mask {
        BONZO, SPIRIT, NONE;
    }
}