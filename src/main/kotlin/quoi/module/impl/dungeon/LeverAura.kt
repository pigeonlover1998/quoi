package quoi.module.impl.dungeon

import quoi.api.events.ChatEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Dungeon.inP3
import quoi.api.skyblock.invoke
import quoi.module.Module
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.NumberSetting
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.player.PlayerUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

// Kyleen
object LeverAura : Module(
    "Lever Aura",
    area = Island.Dungeon(7, inBoss = true)
) {

    private val deviceLevers by BooleanSetting("Lights device")
    private val sectionLevers by BooleanSetting("S1/2/3/4 levers")
    private val delay by NumberSetting("Delay", 400, 0, 1000, 10, "Delay Between lever flips")

    private val leverCooldowns = HashMap<BlockPos, Long>()
    private val interactedSectionLevers = HashSet<BlockPos>()
    private val interactedDeviceLevers = HashSet<BlockPos>()
    private val interactedReflip = HashSet<BlockPos>()

    private val roomLeverPositions = listOf(
        BlockPos(94, 124, 113),
        BlockPos(106, 124, 113),
        BlockPos(27, 124, 127),
        BlockPos(23, 132, 138),
        BlockPos(14, 122, 55),
        BlockPos(2, 122, 55),
        BlockPos(86, 128, 46),
        BlockPos(84, 121, 34)
    )

    private val deviceLeverPositions = listOf(
        BlockPos(60, 134, 142),
        BlockPos(60, 135, 142),
        BlockPos(62, 136, 142),
        BlockPos(62, 133, 142),
        BlockPos(58, 133, 142),
        BlockPos(58, 136, 142)
    )

    private val deviceReflipLever = listOf(BlockPos(60, 134, 142))

    init {
        on<TickEvent.End> {
            if (Dungeon.isDead) return@on

            if (sectionLevers) {
                processLevers(
                    roomLeverPositions,
                    interactedSectionLevers,
                    allowPoweredSkip = true
                )
            }

            if (deviceLevers) {
                processLevers(
                    deviceLeverPositions,
                    interactedDeviceLevers,
                    allowPoweredSkip = false
                )
            }

            if (deviceLevers && inP3) {
                processLevers(
                    deviceReflipLever,
                    interactedReflip,
                    allowPoweredSkip = false
                )
            }
        }


        on<WorldEvent.Change> {
            interactedSectionLevers.clear()
            interactedDeviceLevers.clear()
            interactedReflip.clear()
        }

        on<ChatEvent.Packet> {
            if (message.noControlCodes == "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!") {
                interactedSectionLevers.clear()
                interactedDeviceLevers.clear()
                interactedReflip.clear()
            }
        }
    }

    private fun processLevers(
        positions: List<BlockPos>,
        interactedSet: HashSet<BlockPos>,
        allowPoweredSkip: Boolean
    ) {
        for (pos in positions) {
            if (interactedSet.contains(pos)) continue
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) > 25) continue

            val state = level.getBlockState(pos)
            if (state.block != Blocks.LEVER) continue

            val powered = state.getValue(LeverBlock.POWERED)
            if (powered && allowPoweredSkip) {
                interactedSet.add(pos)
                continue
            }

            if (System.currentTimeMillis() - (leverCooldowns[pos] ?: 0L) < delay) continue

            val hit = BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false)
            PlayerUtils.interact(hitResult = hit, swing = true)

            leverCooldowns[pos] = System.currentTimeMillis()
            interactedSet.add(pos)
            break
        }
    }
}