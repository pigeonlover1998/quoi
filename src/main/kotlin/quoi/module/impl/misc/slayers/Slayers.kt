package quoi.module.impl.misc.slayers

import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.trackedBy
import quoi.module.Module
import quoi.module.impl.misc.slayers.blaze.BlazeSlayer
import quoi.utils.EntityUtils.getEntities
import quoi.utils.EntityUtils.getEntity
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.romanToInt

object Slayers : Module(
    "Slayers"
) {

    private val slayers = setOf(
        BlazeSlayer
    ).flatMap { it.features }

    override fun onDisable() {
        slayers.forEach { it.onDisable() }
        super.onDisable()
    }

    var questTier = 0
        private set

    val questState by trackedBy<PacketEvent.Received, ClientboundSetPlayerTeamPacket, QuestState>(QuestState.NONE) {
        val params = packet.parameters.orElse(null) ?: return@trackedBy it

        val text = (params.playerPrefix.string + params.playerSuffix.string).noControlCodes.trim()
        when {
            text.contains("Combat") || text.contains("Kills") -> QuestState.SPAWNING
            text == "Slay the boss!" -> QuestState.KILLING
            text == "Boss slain!" -> QuestState.SLAIN
            else -> it // can get stuck when boss is slain and you collect the reward but doesn't really matter since I won't be using it prob.
        }
    }

    val currentBoss by trackedBy<TickEvent.End, LivingEntity?>(null) { boss ->
        if (questState != QuestState.KILLING) {
            questTier = 0
            return@trackedBy null
        }

        boss?.let {
            if (!it.isDeadOrDying) return@trackedBy it
            return@trackedBy null
        }

        val spawnedBy = getEntities<ArmorStand>().firstOrNull { stand ->
            val name = stand.displayName?.string ?: return@trackedBy null
            name.contains("Spawned by: ${player.name.string}", ignoreCase = true)
        } ?: return@trackedBy null

        val bossStand = (getEntity(spawnedBy.id - 2) as? ArmorStand)?.displayName?.string
            ?: return@trackedBy null

        questTier = tierRegex.find(bossStand)?.destructured
            ?.let { (tier) -> romanToInt(tier) }
            ?: return@trackedBy null

        val a = getEntity(spawnedBy.id - 3) as? LivingEntity
        a
    }

    private val tierRegex = Regex(".* (I{1,3}|IV|V) \\d+.*❤$")
}