package quoi.api.events

import quoi.api.events.core.Event
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.block.state.BlockState
import quoi.api.skyblock.dungeon.components.Room as DungeonRoom

abstract class DungeonEvent {
    abstract class Secret {
        class Interact(val blockPos: BlockPos, val blockState: BlockState) : Event()
        class Item(val entity: ItemEntity) : Event()
        class Bat(val packet: ClientboundSoundPacket) : Event()
    }

    abstract class Room {
        class Enter(val room: DungeonRoom, val previous: DungeonRoom) : Event()
        class Scan(val room: DungeonRoom) : Event()
    }
}