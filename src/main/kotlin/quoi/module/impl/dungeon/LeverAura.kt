package quoi.module.impl.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.phys.Vec3
import quoi.api.events.ChatEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Dungeon.inP3
import quoi.api.skyblock.invoke
import quoi.module.Module
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.player.AuraManager

// Kyleen
object LeverAura : Module( // todo move to secret aura.. also this shit is completely broken with powered levers rn idk
    "Lever Aura",
    desc = "Automatically flicks levers in Goldor fight.",
    area = Island.Dungeon(7, inBoss = true)
) {

    private val deviceLevers by switch("Lights device")
    private val sectionLevers by switch("S1/2/3/4 levers", desc = "Flips levers on gold pillar things")
    private val ignorePowered by switch("Ignore powered", desc = "Ignores powered lever check")
    private val range by slider("Range", 5.0, 2.1, 6.5, 0.1, desc = "Maximum range for the lever aura.")
    private val delay by slider("Delay", 400, 0, 1000, 10)

    private val leverCooldowns = HashMap<BlockPos, Long>()
    private var hasFlippedReflip = false

    private val roomLevers = listOf(
        BlockPos(94,124,113),
        BlockPos(106,124,113),
        BlockPos(27,124,127),
        BlockPos(23,132,138),
        BlockPos(14,122,55),
        BlockPos(2,122,55),
        BlockPos(86,128,46),
        BlockPos(84,121,34)
    )

    private val deviceLeversPos = listOf(
        BlockPos(60,134,142),
        BlockPos(60,135,142),
        BlockPos(62,136,142),
        BlockPos(62,133,142),
        BlockPos(58,133,142),
        BlockPos(58,136,142)
    )

    private val deviceReflipLever = BlockPos(60,134,142)

    init {
        on<TickEvent.Start> {
            if (Dungeon.isDead) return@on
            if (sectionLevers && inP3) processLevers(roomLevers)
            if (deviceLevers) {
                if (inP3) {
                    if (!hasFlippedReflip) {
                        if (processLevers(listOf(deviceReflipLever), forceIgnorePowered = true)) {
                            hasFlippedReflip = true
                        }
                    }
                } else {
                    processLevers(deviceLeversPos)
                }
            }
        }

        on<WorldEvent.Change> {
            leverCooldowns.clear()
            hasFlippedReflip = false
        }

        on<ChatEvent.Packet> {
            if (message.noControlCodes == "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!") {
                leverCooldowns.clear()
                hasFlippedReflip = false
            }
        }
    }

    private fun processLevers(positions: List<BlockPos>, forceIgnorePowered: Boolean = false): Boolean {
        for (pos in positions) {
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) > range * range) continue

            val state = level.getBlockState(pos)
            if (state.block != Blocks.LEVER) continue

            val powered = state.getValue(LeverBlock.POWERED)
            if (powered && !ignorePowered && !forceIgnorePowered) continue

            val last = leverCooldowns[pos] ?: 0L
            if (System.currentTimeMillis() - last < delay) continue

            AuraManager.auraBlock(pos)
            player.swing(InteractionHand.MAIN_HAND)
            leverCooldowns[pos] = System.currentTimeMillis()

            return true
        }
        return false
    }
}