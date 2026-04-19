package quoi.module.impl.dungeon

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
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
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.level.block.SkullBlock
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.entity.SkullBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.Location.inSkyblock
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Dungeon.currentRoom
import quoi.api.skyblock.dungeon.Dungeon.inDungeons
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.*
import quoi.utils.WorldUtils.state
import quoi.utils.skyblock.player.interact.AuraManager
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.skyblock.player.SwapResult
import java.util.*

// modified https://github.com/Hypericat/NoobRoutes/blob/main/src/main/kotlin/noobroutes/features/dungeon/SecretAura.kt
object SecretAura : Module(
    "Secret Aura",
    desc = "Automatically collects secrets."
) {
    private val chestRange by slider("Chest range", 6.2, 2.1, 6.5, 0.1, desc = "Maximum range for secret aura.")
    private val skullRange by slider("Skull range", 4.7, 2.1, 4.7, 0.1, desc = "Maximum range for secret aura when clicking skulls.")
    private val clickDelay by slider("Click delay", 150, 100, 4000, 50, desc = "Delay before clicking a block.") // this shit doesn't seem to be making any difference tbh...

    private val swapOn by selector("Swap on", "Skulls", arrayListOf("None", "Skulls", "All"), desc = "Makes secret aura swap")
    private val swapBack by switch("Swap back", true, desc = "Makes secret aura swap back to previous item after swapping.").childOf(::swapOn) { it.index >= 1 }
    private val swapSlot by slider("Swap item slot", 1, 1, 9, 1, desc = "Slot for secret aura to swap to.").childOf(::swapOn) { it.index >= 1 }

    private val swing by switch("Swing hand", desc = "Makes secret aura swing hand on click.")
    private val dungeonsOnly by switch("Dungeons only", true, desc = "Makes secret aura only work in dungeons.")
    private val inBoss by switch("In boss", true, desc = "Makes secret aura work in floor 7 boss.")
    private val inContainer by switch("In container", desc = "Makes secret aura work while container is opened.")

    private val REDSTONE_KEY = UUID.fromString("fed95410-aba1-39df-9b95-1d4f361eb66e")
    private val WITHER_ESSENCE = UUID.fromString("e0f3e929-869e-3dca-9504-54c666ee6f23")

    private var redstoneKey = true
    private val clickedBlocks = Long2LongOpenHashMap()
    val blocksDone = LongOpenHashSet()
    private var previousSlot = -1
    var lastClickedPos: BlockPos? = null

    private val levers = listOf(
        BlockPos(94, 124, 113),
        BlockPos(106, 124, 113),
        BlockPos(27, 124, 127),
        BlockPos(23, 132, 138),
        BlockPos(14, 122, 55),
        BlockPos(2, 122, 55),
        BlockPos(86, 128, 46),
        BlockPos(84, 121, 34)
    )

    private val deviceLevers = listOf(
        BlockPos(62, 136, 142),
        BlockPos(62, 133, 142),

        BlockPos(60, 134, 142),
        BlockPos(60, 135, 142),

        BlockPos(58, 133, 142),
        BlockPos(58, 136, 142)
    )

    private val extraDevLever = BlockPos(59, 133, 142)

    override fun onDisable() {
        super.onDisable()
        clear()
    }

    init {
        command.sub("clearaura") {
            clear()
        }

        on<WorldEvent.Change> {
            clear()
        }

        on<TickEvent.End> {
            if (AutoClear.active) return@on
            if (!inSkyblock ||
                (mc.screen != null && !inContainer) ||
                (dungeonsOnly && !inDungeons) ||
                (Dungeon.inBoss && !inBoss)
            ) return@on

            val currentTime = System.currentTimeMillis()
            val iterator = clickedBlocks.long2LongEntrySet().fastIterator()

            while (iterator.hasNext()) {
                val entry = iterator.next()
                val posLong = entry.longKey
                if (blocksDone.contains(posLong) || currentTime - entry.longValue > 2000) {
                    iterator.remove()
                    continue
                }

                val tile = level.getBlockEntity(BlockPos.of(posLong))
                if (tile is ChestBlockEntity && tile.getOpenNess(0f) > 0f) {
                    blocksDone.add(posLong)
                    iterator.remove()
                }
            }

            currentRoom?.let { room ->
                when (room.name) {
                    "Three Weirdos" -> return@on
                    "Ice Path" if (!room.getRealCoords(BlockPos(15, 68, 25)).state.isAir) -> return@on
                    "Water Board" if ((15..19).any { !room.getRealCoords(BlockPos(15, 57, it)).state.isAir }) -> return@on
                    "Teleport Maze" -> {
                        val min = room.getRealCoords(Vec3(12, 68, 14))
                        val max = room.getRealCoords(Vec3(18, 70, 20))
                        if (!AABB(min, max).intersects(player.blockPosition().aabb)) return@on
                    }
                    "Ice Fill" if (room.getRealCoords(BlockPos(15, 71, 26)).state.block != Blocks.PACKED_ICE) -> return@on
                }
            }

            var blockCandidate = BlockDistance(Blocks.AIR, BlockPos(Int.MAX_VALUE, 69, Int.MIN_VALUE), Double.POSITIVE_INFINITY)
            val eyePos = player.eyePosition()

            val sqEssence = skullRange * skullRange
            val sqChest = chestRange * chestRange
            val maxRange = maxOf(chestRange, skullRange)

            val searchBox = AABB(eyePos, eyePos).inflate(maxRange)
            val minPos = BlockPos.containing(searchBox.minX, searchBox.minY, searchBox.minZ)
            val maxPos = BlockPos.containing(searchBox.maxX, searchBox.maxY, searchBox.maxZ)

            for (pos in BlockPos.betweenClosed(minPos, maxPos)) {
                val posLong = pos.asLong()
                if (blocksDone.contains(posLong)) continue

                val state = pos.state
                val block = state.block

                if (Dungeon.inBoss && !pos.isBossBlock(state)) continue

                if (!isValidBlock(block, pos)) continue

                val nextClickTime = clickedBlocks[posLong]
                if (clickDelay > 0 && nextClickTime == 0L) {
                    clickedBlocks[pos.immutable().asLong()] = currentTime + clickDelay
                    continue
                }

                if (nextClickTime != 0L && nextClickTime > currentTime) continue

                val currentDistanceSq = eyePos.distanceToSqr(pos.center)

                if ((block is SkullBlock && currentDistanceSq > sqEssence) || currentDistanceSq > sqChest) continue

                if (currentDistanceSq < blockCandidate.distanceSq) {
                    blockCandidate = BlockDistance(block, pos.immutable(), currentDistanceSq)
                }
            }
            currentRoom?.let { room ->
                when (room.name) {
                    "Water Board", "Tic Tac Toe" -> if (blockCandidate.block == Blocks.LEVER) return@on
                    "Lower Blaze" -> {
                        val chest = room.getRealCoords(BlockPos(15, 20, 15))
                        if (chest.x == blockCandidate.pos.x && chest.state.block != Blocks.CHEST) return@on
                    }
                    "Higher Blaze" -> {
                        val chest = room.getRealCoords(BlockPos(15, 119, 15))
                        if (chest.x == blockCandidate.pos.x && chest.state.block != Blocks.CHEST) return@on
                    }
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

            clickedBlocks[blockCandidate.pos.asLong()] = System.currentTimeMillis() + 500

            lastClickedPos = blockCandidate.pos
            AuraManager.interactBlock(blockCandidate.pos)
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
                                blocksDone.add(entity.blockPosition().offset(0, 2, 0).immutable().asLong())
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
        val level = mc.level ?: return
        val state = level.getBlockState(pos)
        val currentBlock = state.block

        if (currentBlock === Blocks.LEVER) {
            val bossLev = pos in levers || pos in deviceLevers || pos == extraDevLever
            if (!bossLev) {
                blocksDone.add(pos.asLong())
            }
            return
        }

        if (state.block is SkullBlock && packetState.block === Blocks.AIR) {
            val skullEntity = level.getBlockEntity(pos) as? SkullBlockEntity ?: return
            val profileId = skullEntity.ownerProfile?.partialProfile()?.id ?: return

            if (profileId == REDSTONE_KEY) redstoneKey = true
            return
        }

        if (currentBlock === Blocks.REDSTONE_BLOCK) {
            blocksDone.add(pos.asLong())
        }
    }

    private fun BlockPos.isBossBlock(state: BlockState): Boolean {
        if (state.block != Blocks.LEVER) return false
        val p3Lever = this in levers && EntityUtils.getEntities<ArmorStand>(vec3.aabb(0.5)) { it.displayName?.string == "Not Activated" }.isNotEmpty()
        val devLever = this in deviceLevers && state.hasProperty(LeverBlock.POWERED) && !state.getValue(LeverBlock.POWERED)
        val extraDevLever = this == extraDevLever && !devLever && EntityUtils.getEntities<ArmorStand>(vec3.aabb(2.0)) { it.displayName?.string == "Inactive" }.isNotEmpty() // untested
        return p3Lever || devLever || extraDevLever
    }

    fun clear() {
        blocksDone.clear()
        clickedBlocks.clear()
        redstoneKey = false
        previousSlot = -1
    }

    private data class BlockDistance(val block: Block, val pos: BlockPos, val distanceSq: Double)
}