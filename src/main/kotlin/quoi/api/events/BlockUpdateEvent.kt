package quoi.api.events

import quoi.api.events.core.Event
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

class BlockUpdateEvent(val pos: BlockPos, val old: BlockState, val updated: BlockState) : Event()