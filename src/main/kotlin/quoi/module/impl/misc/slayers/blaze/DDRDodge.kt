package quoi.module.impl.misc.slayers.blaze

import net.minecraft.world.entity.monster.Blaze
import net.minecraft.world.level.block.state.BlockState
import quoi.api.events.AreaEvent
import quoi.api.events.GuiEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.module.impl.misc.slayers.QuestState
import quoi.module.impl.misc.slayers.Slayers
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.ChatUtils.modMessage
import quoi.utils.WorldUtils.blocksBelow
import quoi.utils.WorldUtils.nearbyBlocks
import quoi.utils.WorldUtils.registryName
import quoi.utils.WorldUtils.solid
import quoi.utils.WorldUtils.state
import quoi.utils.skyblock.player.MovementUtils
import quoi.utils.skyblock.player.MovementUtils.moveTo

object DDRDodge : ToggleableGroup(BlazeSlayer, "DDR dodge", desc = "Automatically dodges terracotta crosses on the floor.") { // untested

    init {
        on<TickEvent.End> {
            if (Slayers.questState != QuestState.KILLING) return@on
            if (mc.screen != null || !BlazeSlayer.blazeBoss.canDDR) return@on
            val a = player.blocksBelow { _, state -> state.isTerra }.firstOrNull()?.first ?: return@on

            val safePos =
                player.position().nearbyBlocks(2.0f) { !it.state.isTerra && it.solid }
                    .minByOrNull { it.center.distanceToSqr(player.position()) }?.center
                    ?: return@on modMessage("no safe blocks")

            val dir = safePos.subtract(a.center).normalize()
            val safe = safePos.add(dir.scale(0.3))
            player.moveTo(safe)
        }

        on<AreaEvent.Sub> {
            MovementUtils.cancelMovementTask()
        }

        on<GuiEvent.Open> {
            MovementUtils.cancelMovementTask()
        }
    }

    override fun onDisable() {
        MovementUtils.cancelMovementTask()
    }

    private inline val BlockState.isTerra: Boolean
        get() = block.registryName.contains("terracotta")

    private val Blaze?.canDDR: Boolean
        get() {
            if (this == null || isInvisible || Slayers.questTier < 3) return false
            val maxHp = if (Slayers.questTier == 3) 45_000_000 else 150_000_000
            return health <= maxHp / 3
        }
}