package quoi.module.impl.dungeon

import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Dungeon.inBoss
import quoi.module.Module
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.NumberSetting
import quoi.utils.Ticker
import quoi.utils.equalsOneOf
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.skyblock.player.SwapResult
import quoi.utils.ticker
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import java.util.HashSet

// Kyleen
object SecretTriggerBot : Module(
    "Secret TriggerBot",
    area = Island.Dungeon
) {

    private val swapSlot by NumberSetting("Swap slot", 1, 1, 9, 1, desc = "Hotbar slot to swap to (1-9).")
    private val swapBack by BooleanSetting("Swap back", desc = "Swaps back to original slot after clicking.")
    private val reactionDelay by NumberSetting("Interact delay", 0, 0, 5, 1, desc = "Ticks to wait before triggering.")

    private val clickedBlocks = HashSet<BlockPos>()

    override fun onDisable() {
        clickedBlocks.clear()
        super.onDisable()
    }

    init {
        on<WorldEvent.Change> {
            clickedBlocks.clear()
        }

        on<TickEvent.End> {
            if (mc.screen != null || inBoss) return@on

            tBotTicker?.let {
                if (it.tick()) tBotTicker = null
                return@on
            }

            val result = mc.hitResult
            if (result !is BlockHitResult || result.type != HitResult.Type.BLOCK) return@on

            val pos = result.blockPos
            val state = level.getBlockState(pos)

            if (Dungeon.isSecret(state, pos) && !clickedBlocks.contains(pos)) {
                tBotTicker = triggerBotTicker(pos)
            }

        }
    }

    private var tBotTicker: Ticker? = null

    fun triggerBotTicker(pos: BlockPos) = ticker {

        val desiredSlot = swapSlot - 1
        val originalSlot = player.inventory.selectedSlot
        delay(reactionDelay)
        await {
            if (player.inventory.selectedSlot == desiredSlot) return@await true
            val result = SwapManager.swapToSlot(desiredSlot)
            result.equalsOneOf(SwapResult.ALREADY_SELECTED, SwapResult.SUCCESS)
        }
        action {
            val result = mc.hitResult
            if (result is BlockHitResult && result.blockPos == pos) {
                mc.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, result)
                player.swing(InteractionHand.MAIN_HAND)
                clickedBlocks.add(pos)
            }
        }

        if (swapBack) {
            await {
                if (originalSlot == -1 || originalSlot !in 0..8) return@await true
                if (player.inventory.selectedSlot == originalSlot) return@await true

                val result = SwapManager.swapToSlot(originalSlot)
                result.equalsOneOf(SwapResult.ALREADY_SELECTED, SwapResult.SUCCESS)
            }
        }
    }
}