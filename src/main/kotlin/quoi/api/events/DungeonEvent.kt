package quoi.api.events

import quoi.api.events.core.Event
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.block.state.BlockState
import quoi.api.skyblock.dungeon.enums.Stage
import quoi.api.skyblock.dungeon.enums.Floor
import quoi.api.skyblock.dungeon.enums.Phase
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomState

abstract class DungeonEvent {
    class Enter(val floor: Floor) : Event()
    class Start : Event()

    abstract class Secret {
        class Interact(val blockPos: BlockPos, val blockState: BlockState) : Event()
        class Item(val entity: ItemEntity) : Event()
        class Bat(val packet: ClientboundSoundPacket) : Event()
    }

    abstract class Room {
        class Enter(val room: OdonRoom?) : Event()
        class Scan(val room: OdonRoom) : Event()
        class State(val room: OdonRoom, val old: RoomState, val new: RoomState, val current: Boolean) : Event()
    }

    class DoorOpen(val opener: String) : Event()

    class PhaseComplete(val phase: Phase) : Event()

    class StageComplete(val stage: Stage) : Event() {
        class Full(val stage: Stage) : Event()
    }
}