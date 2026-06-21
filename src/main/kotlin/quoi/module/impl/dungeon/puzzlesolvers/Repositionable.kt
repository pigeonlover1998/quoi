package quoi.module.impl.dungeon.puzzlesolvers

import net.minecraft.core.BlockPos
import quoi.QuoiMod.mc
import quoi.utils.Ticker
import quoi.utils.player
import quoi.utils.skyblock.item.TeleportUtils.getEtherwarpDirection
import quoi.utils.skyblock.player.MovementUtils.isMoving
import quoi.utils.skyblock.player.PlayerUtils.at
import quoi.utils.skyblock.player.PlayerUtils.useItem
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.ticker

interface Repositionable {
    var repositionTicker: Ticker?

    fun reposition(
        spot: BlockPos,
        bow: Boolean = true,
        stand: Boolean = false,
        awaitStand: Boolean = false
    ) {
        if (repositionTicker != null) return
        if (stand && player.isMoving) return

        val dir = getEtherwarpDirection(spot) ?: return

        repositionTicker = ticker {
            val r = SwapManager.swapById("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END").success
            if (!mc.options.keyShift.isDown) {
                action { mc.options.keyShift.isDown = true }
                delay(2)
            }

            if (awaitStand) await { !player.isMoving }

            action {
                if (!r) cancel()
                player.useItem(dir)
            }
            await { player.at(spot) }

            if (bow) {
                action { SwapManager.swapByLore("Shortbow: Instantly shoots!") }
            } else {
                action { mc.options.keyShift.isDown = false }
                delay(2)
            }
            repositionTicker = null
        }
    }
}