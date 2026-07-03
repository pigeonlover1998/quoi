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

@Suppress("unused_expression")
object Slayers : Module(
    "Slayers"
) {
    init {
        BlazeSlayer
    }

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
        if (questState != QuestState.KILLING) return@trackedBy null
        if (boss?.isDeadOrDying == false) return@trackedBy boss
        val spawnedBy = getEntities<ArmorStand>().firstOrNull { stand ->
            val name = stand.displayName?.string?.noControlCodes ?: return@trackedBy null
            name.contains("Spawned by: ${player.name.string}", ignoreCase = true)
        } ?: return@trackedBy null

        getEntity(spawnedBy.id - 3) as? LivingEntity
    }

}