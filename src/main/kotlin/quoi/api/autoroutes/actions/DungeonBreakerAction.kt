package quoi.api.autoroutes.actions

import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.api.skyblock.dungeon.Dungeon
import quoi.config.TypeName
import quoi.module.impl.dungeon.AutoRoutes
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.wait
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.WorldUtils.state
import quoi.utils.skyblock.ItemUtils.lore
import quoi.utils.skyblock.ItemUtils.skyblockId
import quoi.utils.skyblock.player.SwapManager

@TypeName("dungeon_breaker")
class DungeonBreakerAction(val blocks: List<BlockPos> = emptyList()) : RingAction {

    @Transient
    private val recentlyBroken = mutableMapOf<BlockPos, Long>()

    override val colour: Colour
        get() = Colour.ORANGE

    override suspend fun execute(player: LocalPlayer) {
        val level = mc.level ?: return
        val room = Dungeon.currentRoom ?: return
        val ring = AutoRoutes.routes[room.data.name]?.find { it.action == this } ?: return

        clearCooldownCache()

        if (blocks.isEmpty()) return

        val needsBreaking = blocks.any { relativePos ->
            val realPos = room.getRealCoords(relativePos)
            !recentlyBroken.containsKey(realPos) &&
            level.isLoaded(realPos) &&
            !realPos.state.isAir
        }
        if (!needsBreaking) return

        val breakerSlot = (0..8).find { slot -> // todo make it a util
            getBreakerCharges(player.inventory.getItem(slot)) > 0
        }

        if (breakerSlot == null) {
            modMessage("&cDungeon breaker not found or has no charges.")
            return
        }

        val initialCharges = getBreakerCharges(player.inventory.getItem(breakerSlot))
        var chargesUsed = 0

        if (player.inventory.selectedSlot != breakerSlot) {
            if (!SwapManager.swapToSlot(breakerSlot).success) return
            wait(1)
        }

        for (relativePos in blocks) {
            if (chargesUsed >= initialCharges) {
                modMessage("&eStopping. Out of charges.")
                break
            }

            val realPos = room.getRealCoords(relativePos)

            if (recentlyBroken.containsKey(realPos)) continue

            if (!level.isLoaded(realPos) || realPos.state.isAir) continue

//            if (realPos.distToCenterSqr(player.eyePosition) > 25.0) continue
            var outOfRangeTicks = 0
            while (realPos.distToCenterSqr(player.eyePosition) > 30.0) {
                wait(1)
                outOfRangeTicks++
                if (!ring.inside(room) || outOfRangeTicks > 40) return modMessage("&cStopping. Out of range.")
            }

            mc.connection?.send(
                ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    realPos,
                    Direction.UP
                )
            )
            player.swing(InteractionHand.MAIN_HAND)
            recentlyBroken[realPos] = System.currentTimeMillis()
            chargesUsed++
            if (!AutoRoutes.zeroTickDb) wait(1)
        }
    }

    private fun clearCooldownCache() {
        val now = System.currentTimeMillis()
        recentlyBroken.entries.removeIf { (pos, time) -> now - time > 10_000 || !pos.state.isAir }
    }

    private fun getBreakerCharges(stack: ItemStack): Int { // todo make it a util
        if (stack.isEmpty || stack.skyblockId != "DUNGEONBREAKER") return 0

        val lore = stack.lore ?: return 0
        val loreStringList = lore.asSequence().map { it.noControlCodes }

        val charges = loreStringList.firstNotNullOfOrNull { line ->
            Regex("Charges: (\\d+)/(\\d+)⸕").find(line)?.groupValues?.get(1)?.toIntOrNull()
        } ?: 0

        return charges
    }
}