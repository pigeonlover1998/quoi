package quoi.module.impl.misc.slayers.blaze

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.world.entity.monster.Blaze
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.AreaEvent
import quoi.api.events.BlockEvent
import quoi.api.events.GuiEvent
import quoi.api.events.KeyEvent
import quoi.api.events.PacketEvent
import quoi.api.events.RenderEvent
import quoi.api.events.SlayerEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.module.impl.misc.slayers.QuestState
import quoi.module.impl.misc.slayers.Slayers
import quoi.module.impl.misc.slayers.blaze.BlazeSlayer.blazeBoss
import quoi.module.impl.misc.slayers.blaze.BlazeSlayer.demons
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.StringUtils.containsOneOf
import quoi.utils.WorldUtils.blocksAtFeet
import quoi.utils.WorldUtils.nearbyBlocks
import quoi.utils.WorldUtils.registryName
import quoi.utils.WorldUtils.state
import quoi.utils.WorldUtils.ticksUntilCollision
import quoi.utils.aabb
import quoi.utils.distanceToSqr
import quoi.utils.render.drawFilledBox
import quoi.utils.skyblock.player.MovementUtils.cancelMovementTask
import quoi.utils.skyblock.player.MovementUtils.moveTo

// todo improve fire dodge shit. make it walk away from pigman and out of fire similar to ddr
object DamageDodge : ToggleableGroup(BlazeSlayer, "Damage dodge", desc = "Automatically dodges or prevents damage.") {
    private val ddr by switch("DDR")
    private val fire by switch("Fire")

    private val fireBlocks = mutableListOf<BlockPos>()
    private val ddrBlocks = mutableListOf<BlockPos>()

    init {
        on<TickEvent.End> {
            if (Slayers.questState != QuestState.KILLING) return@on
            if (mc.screen != null) return@on

            if (ddr && blazeBoss.canDDR) player.blocksAtFeet(-1) { it in ddrBlocks }.firstOrNull()?.let { a ->
                val safePos =
                    player.position().nearbyBlocks(2.0f) { it !in ddrBlocks && it !in fireBlocks && !it.state.isAir }
                        .minByOrNull { it.center.distanceToSqr(player.position()) }?.center
                        ?: return@let modMessage("no safe blocks")

                val dir = safePos.subtract(a.center).normalize()
                val safe = safePos.add(dir.scale(0.3))
                player.moveTo(safe)
            }

//            if (fire) demons?.second?.let { pig ->
//                if (player.distanceToSqr(pig) > 3) return@let
//                val safePos =
//                    player.position().nearbyBlocks(2.0f) { it !in ddrBlocks && it !in fireBlocks && it.solid }
//                        .minByOrNull { it.center.distanceToSqr(player.position()) }?.center
//                        ?: return@let modMessage("no safe blocks")
//
//                val dir = safePos.subtract(pig.position()).normalize()
//                val safe = safePos.add(dir.scale(0.3))
//                player.moveTo(safe)
//            }
        }

        on<BlockEvent.Update> {
            if (Slayers.questState != QuestState.KILLING) return@on
//            if (demons == null && !blazeBoss.canDDR) return@on

            if (ddr) {
                if (pos in ddrBlocks && old.isTerra && !updated.isTerra) {
                    scheduleTask {
                        val ddr = pos.nearbyBlocks(3f) { it in ddrBlocks }
                        if (ddr.size > 3) ddrBlocks.removeAll(ddr)
                    }
                    return@on
                }

                if (!old.isTerra && updated.isTerra) {
                    scheduleTask {
                        val ddr = pos.nearbyBlocks(3f) { it.state.isTerra }
                        if (ddr.size > 3) ddrBlocks.addAll(ddr)
                    }
                    return@on
                }
            }

            if (fire) {
                if (pos in fireBlocks && old.isFire && !updated.isFire) {
                    fireBlocks.remove(pos)
                    return@on
                }

                demons?.second?.let { pig ->
                    if (pig.blockPosition().distanceToSqr(pos) > 2 || !updated.isFire) return@let
                    fireBlocks.add(pos)
                }
            }
        }

        on<KeyEvent.Input>(Priority.LOW) {
            if (Slayers.questState != QuestState.KILLING || !fire || !input.moving) return@on
            if (player.blocksAtFeet { it in fireBlocks }.any()) return@on

            val closest = player.position().nearbyBlocks(5f) { it in fireBlocks }.firstOrNull() ?: return@on
            val ticks = ticksUntilCollision(closest) ?: return@on
            if (ticks <= 1.5) {
                input.stop()
//                input.shift = true
            }
        }

//        on<RenderEvent.World> { // temp
//            fireBlocks.forEach {
//                ctx.drawFilledBox(it.aabb, Colour.CYAN, depth = true)
//            }
//            ddrBlocks.forEach {
//                ctx.drawFilledBox(it.aabb, Colour.PINK.withAlpha(50), depth = true)
//            }
//        }
//
//        on<PacketEvent.Received, ClientboundLevelParticlesPacket> { // temp
//            cancel()
//        }

        on<SlayerEvent.State> {
            if (new == QuestState.SLAIN) onDisable()
        }

        on<AreaEvent.Sub> {
            onDisable()
        }

        on<GuiEvent.Open> {
            cancelMovementTask()
        }
    }

    override fun onDisable() {
        cancelMovementTask()
        fireBlocks.clear()
        ddrBlocks.clear()
    }

    private inline val BlockState.isTerra: Boolean
        get() = block.registryName.containsOneOf("yellow_terr", "red_terr", "brown_terr")

    private inline val BlockState.isFire: Boolean
        get() = block == Blocks.FIRE

    private val Blaze?.canDDR: Boolean
        get() {
            if (this == null || isInvisible || Slayers.questTier < 3) return false
            val maxHp = if (Slayers.questTier == 3) 45_000_000 else 150_000_000
            return health <= maxHp / 3
        }
}