package quoi.api.events

import quoi.api.events.core.Event
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.state.BlockState
import quoi.api.events.core.CancellableEvent

abstract class BlockEvent {
    class Update(val pos: BlockPos, val old: BlockState, val updated: BlockState) : Event()

    abstract class Destroy {
        class Start(val pos: BlockPos, val direction: Direction) : CancellableEvent()
    }
}