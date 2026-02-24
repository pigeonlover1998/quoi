package quoi.module.impl.dungeon

import quoi.api.events.TickEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon.currentP3Section
import quoi.api.skyblock.dungeon.Dungeon.inP3
import quoi.api.skyblock.dungeon.Dungeon.isDead
import quoi.api.skyblock.invoke
import quoi.module.Module
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.skyblock.player.SwapResult
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

// Kyleen
object BarrierBoom : Module( // todo move to triggerbot module
    "Barrier Boom",
    desc = "Automatically blows up Goldor fight gates.",
    area = Island.Dungeon(7, inBoss = true)
) {

    private var hasClickedBarrier = false

    init {
        on<TickEvent.Start> {
            if (mc.screen != null || isDead || !inP3 || currentP3Section !in 1..3) return@on

            val result = mc.hitResult
            if (result is BlockHitResult && result.type == HitResult.Type.BLOCK) {
                val state = level.getBlockState(result.blockPos)

                if (state.block == Blocks.BARRIER && !hasClickedBarrier) {
                    val swap = SwapManager.swapById("INFINITE_SUPERBOOM_TNT", "SUPERBOOM_TNT")

                    if (swap == SwapResult.SUCCESS || swap == SwapResult.ALREADY_SELECTED) {
                        mc.gameMode?.startDestroyBlock(result.blockPos, result.direction)
                        player.swing(InteractionHand.MAIN_HAND)
                        hasClickedBarrier = true
                    }
                }
            } else {
                hasClickedBarrier = false
            }
        }
    }
}