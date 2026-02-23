package quoi.api.skyblock

import quoi.QuoiMod.mc
import quoi.api.events.ChatEvent
import quoi.api.events.core.EventBus
import net.minecraft.world.entity.ai.attributes.Attributes
import kotlin.math.floor

object SkyblockPlayer {
    inline val health: Int
        get() = mc.player?.let { (maxHealth * it.health / it.maxHealth).toInt() } ?: 0
    var maxHealth: Int = 0
    var absorption: Int = 0

    var defence: Int = 0

    var mana: Int = 0
    var maxMana: Int = 0

    var overflowMana: Int = 0
    var stacks: String = ""
    var salvation: Int = 0

    var effectiveHealth: Int = 0
    inline val speed: Int
        get() = floor(
            (mc.player?.getAttribute(Attributes.MOVEMENT_SPEED)?.baseValue?.toFloat() ?: 0f) * 1000f
        ).toInt()

    var manaUsage: String = ""

    var currentSecrets: Int = -1
    var maxSecrets: Int = -1

    val HP_REGEX = Regex("§[c6]([\\d,]+)/([\\d,]+)❤") // §c1389/1390❤ , §62181/1161❤
    val DEF_REGEX = Regex("§a([\\d,]+)§a❈ Defense") // §a593§a❈ Defense
    val MANA_REGEX = Regex("§b([\\d,]+)/([\\d,]+)✎( Mana)?") // §b550/550✎ Mana§r

    val OVERFLOW_REGEX = Regex("§3([\\d,]+)ʬ") // §3100ʬ
    val STACKS_REGEX = Regex("§6([0-9]+[ᝐ⁑Ѫ])") // §610⁑
    val SALVATION_REGEX = Regex("T([1-3])!") // no idea

    val MANA_USAGE_REGEX = Regex("§b-[\\d,]+ Mana \\(§6.+?§b\\)|§c§lNOT ENOUGH MANA") // §b-50 Mana (§6Speed Boost§b) , §c§lNOT ENOUGH MANA
    val SECRETS_REGEX = Regex("\\s*§7(\\d+)/(\\d+) Secrets") // §76/10 Secrets§r

    fun init() {
        EventBus.on<ChatEvent.ActionBar> {
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
    }
}