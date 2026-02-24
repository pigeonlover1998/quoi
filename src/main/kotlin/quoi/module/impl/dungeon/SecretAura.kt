package quoi.module.impl.dungeon

import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.Location.inSkyblock
import quoi.api.skyblock.dungeon.Dungeon.inBoss
import quoi.api.skyblock.dungeon.Dungeon.inDungeons
import quoi.module.Module
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.NumberSetting
import quoi.module.settings.impl.SelectorSetting
import quoi.utils.equalsOneOf
import quoi.utils.skyblock.player.AuraManager
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.skyblock.player.SwapResult
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SkullBlock
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.entity.SkullBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import java.util.*

// modified https://github.com/Hypericat/NoobRoutes/blob/main/src/main/kotlin/noobroutes/features/dungeon/SecretAura.kt
object SecretAura : Module(
    "Secret Aura",
    desc = "Automatically collects secrets."
) {
    private val chestRange by NumberSetting("Chest range", 6.2, 2.1, 6.5, 0.1, desc = "Maximum range for secret aura.")
    private val skullRange by NumberSetting("Skull range", 4.7, 2.1, 4.7, 0.1, desc = "Maximum range for secret aura when clicking skulls.")
    private val clickDelay by NumberSetting("Click delay", 150, 100, 4000, 50, desc = "Delay before clicking a block.") // this shit doesn't seem to be making any difference tbh...

    private val swapOn by SelectorSetting("Swap on", "Skulls", arrayListOf("None", "Skulls", "All"), desc = "Makes secret aura swap")
    private val swapBack by BooleanSetting("Swap back", true, desc = "Makes secret aura swap back to previous item after swapping.").withDependency { swapOn.index >= 1 }
    private val swapSlot by NumberSetting("Swap item slot", 1, 1, 9, 1, desc = "Slot for secret aura to swap to.").withDependency { swapOn.index >= 1 }

    private val swing by BooleanSetting("Swing hand", desc = "Makes secret aura swing hand on click.")
    private val dungeonsOnly by BooleanSetting("Dungeons only", true, desc = "Makes secret aura only work in dungeons.")

    private val REDSTONE_KEY = UUID.fromString("fed95410-aba1-39df-9b95-1d4f361eb66e")
    private val WITHER_ESSENCE = UUID.fromString("e0f3e929-869e-3dca-9504-54c666ee6f23")

    private var redstoneKey = true
    private val clickedBlocks = mutableMapOf<BlockPos, Long>()
    val blocksDone = mutableSetOf<BlockPos>()
    var previousSlot = -1
    var lastClickedPos: BlockPos? = null


    private data class BlockDistance(val block: Block, val pos: BlockPos, val distanceSq: Double)

    override fun onDisable() {
        super.onDisable()
        clear()
    }

    init {
        on<WorldEvent.Change> {
            clear()
        }

        on<TickEvent.End> {
            if (mc.screen != null || !inSkyblock || inBoss || (!inDungeons && dungeonsOnly)) return@on

            clickedBlocks.entries.removeIf { (pos, time) ->

                if (blocksDone.contains(pos) || System.currentTimeMillis() - time > 2000) {
                    return@removeIf true
                }

                val tile = level.getBlockEntity(pos)
                if (tile is ChestBlockEntity && tile.getOpenNess(0f) > 0f) {
                    blocksDone.add(pos)
                    return@removeIf true
                }

                false
            }

            var blockCandidate = BlockDistance(Blocks.AIR, BlockPos(Int.MAX_VALUE, 69, Int.MIN_VALUE), Double.POSITIVE_INFINITY)
            val eyePos = player.eyePosition

            val sqEssence = skullRange * skullRange
            val sqChest = chestRange * chestRange
            val maxRange = maxOf(chestRange, skullRange)

            val searchBox = AABB(eyePos, eyePos).inflate(maxRange)
            val minPos = BlockPos.containing(searchBox.minX, searchBox.minY, searchBox.minZ)
            val maxPos = BlockPos.containing(searchBox.maxX, searchBox.maxY, searchBox.maxZ)

            for (pos in BlockPos.betweenClosed(minPos, maxPos)) {
                if (blocksDone.contains(pos)) continue

                val currentBlock = level.getBlockState(pos).block
                if (!isValidBlock(currentBlock, pos)) continue

                val nextClickTime = clickedBlocks[pos]
                if (clickDelay > 0 && nextClickTime == null) {
                    clickedBlocks[pos.immutable()] = System.currentTimeMillis() + clickDelay
                    continue
                }

                if (nextClickTime != null && nextClickTime > System.currentTimeMillis()) continue

                val currentDistanceSq = eyePos.distanceToSqr(pos.center)

                if ((currentBlock is SkullBlock && currentDistanceSq > sqEssence) || currentDistanceSq > sqChest) continue

                if (currentDistanceSq < blockCandidate.distanceSq) {
                    blockCandidate = BlockDistance(currentBlock, pos.immutable(), currentDistanceSq)
                }
            }
            if (blockCandidate.block == Blocks.AIR) {
                if (previousSlot != -1) {
                    SwapManager.swapToSlot(previousSlot)
                    previousSlot = -1
                }
                return@on
            }

            when (swapOn.selected) {
                "Skulls" -> {
                    if (blockCandidate.block is SkullBlock) {
                        if (handleSwap() == SwapResult.SUCCESS) return@on
                    }
                }
                "All" -> {
                    if (handleSwap() == SwapResult.SUCCESS) return@on
                }
            }

            clickedBlocks[blockCandidate.pos] = System.currentTimeMillis() + 500

            lastClickedPos = blockCandidate.pos
            AuraManager.auraBlock(blockCandidate.pos)
            if (swing && !player.isShiftKeyDown) player.swing(InteractionHand.MAIN_HAND)
        }

        on<PacketEvent.Received> {
            when (packet) {
//                is ClientboundBlockEventPacket -> { // hypixel doesn't seem to be sending these
//                    if (packet.block == Blocks.CHEST) blocksDone.add(packet.pos.immutable())
//                }

                is ClientboundBlockUpdatePacket -> {
                    handleChangedBlock(packet.blockState, packet.pos.immutable())
                }

                is ClientboundSectionBlocksUpdatePacket -> {
                    packet.runUpdates { pos, state ->
                        handleChangedBlock(state, pos.immutable())
                    }
                }

                is ClientboundSetEquipmentPacket -> {
                    val entity = level.getEntity(packet.entity) as? ArmorStand ?: return@on

                    packet.slots.forEach { pair ->
                        if (pair.first == EquipmentSlot.HEAD) {
                            val itemStack = pair.second
                            val profile = itemStack.get(DataComponents.PROFILE)

                            if (profile?.partialProfile()?.id == WITHER_ESSENCE) {
                                blocksDone.add(entity.blockPosition().offset(0, 2, 0).immutable())
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleSwap(): SwapResult {
        if (previousSlot == -1 && swapBack) previousSlot = player.inventory.selectedSlot
        return SwapManager.swapToSlot((swapSlot - 1).coerceIn(0, 8))
    }

    private fun isValidBlock(block: Block, position: BlockPos): Boolean {
        return when (block) {
            Blocks.AIR -> false
            Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.LEVER -> true
            Blocks.REDSTONE_BLOCK -> redstoneKey
            is SkullBlock -> {
                (mc.level?.getBlockEntity(position) as? SkullBlockEntity)?.ownerProfile?.partialProfile()?.id
                    ?.equalsOneOf(WITHER_ESSENCE, REDSTONE_KEY) ?: false
            }
            else -> false
        }
    }

    private fun handleChangedBlock(packetState: BlockState, pos: BlockPos) {
        val state = level.getBlockState(pos)
        val currentBlock = state.block

        if (currentBlock === Blocks.LEVER) {
            blocksDone.add(pos)
            return
        }

        if (state.block is SkullBlock && packetState.block === Blocks.AIR) {
            val skullEntity = level.getBlockEntity(pos) as? SkullBlockEntity ?: return
            val profileId = skullEntity.ownerProfile?.partialProfile()?.id ?: return

            if (profileId == REDSTONE_KEY) redstoneKey = true
            return
        }

        if (currentBlock === Blocks.REDSTONE_BLOCK) {
            blocksDone.add(pos)
        }
    }

    fun clear() {
        blocksDone.clear()
        clickedBlocks.clear()
        redstoneKey = false
        previousSlot = -1
    }
}