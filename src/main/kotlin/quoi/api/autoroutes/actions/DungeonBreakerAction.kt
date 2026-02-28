package quoi.api.autoroutes.actions

import quoi.QuoiMod.mc
import quoi.api.skyblock.dungeon.Dungeon
import quoi.config.TypeName
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.wait
import quoi.utils.WorldUtils.state
import quoi.utils.skyblock.player.SwapManager
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.Vec3
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.ItemUtils.lore
import quoi.utils.skyblock.ItemUtils.skyblockId

@TypeName("dungeon_breaker")
class DungeonBreakerAction(val blocks: List<BlockPos> = emptyList()) : RingAction {

    @Transient
    private val recentlyBroken = mutableMapOf<BlockPos, Long>()

    override suspend fun execute(player: LocalPlayer) {
        val level = mc.level ?: return
        val room = Dungeon.currentRoom ?: return

        clearCooldownCache()

        if (blocks.isEmpty()) return

        val needsBreaking = blocks.any { relativePos ->
            val realPos = room.getRealCoords(relativePos)
            !recentlyBroken.containsKey(realPos) &&
            level.isLoaded(realPos) &&
            realPos.state?.isAir == false
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
                modMessage("&eStopping. Out of chargs.")
                break
            }

            val realPos = room.getRealCoords(relativePos)

            if (recentlyBroken.containsKey(realPos)) continue

            if (!level.isLoaded(realPos) || realPos.state?.isAir == true) continue

            if (realPos.distToCenterSqr(player.x, player.y, player.z) > 25.0) continue

            val clipResult = level.clip(
                ClipContext(
                    player.eyePosition,
                    Vec3.atCenterOf(realPos),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
                )
            )

            if (/*clipResult.type == HitResult.Type.BLOCK && */clipResult.blockPos == realPos)  {
                mc.connection?.send(
                    ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                        realPos,
                        clipResult.direction
                    )
                )

                mc.execute {
                    player.swing(InteractionHand.MAIN_HAND)
                    level.removeBlock(realPos, false)
                }

                recentlyBroken[realPos] = System.currentTimeMillis()
                chargesUsed++
                wait(1)
            }
        }
    }

    private fun clearCooldownCache() {
        val now = System.currentTimeMillis()
        recentlyBroken.entries.removeIf { now - it.value > 10_000 }
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