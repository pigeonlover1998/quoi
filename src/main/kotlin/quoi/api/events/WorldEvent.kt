package quoi.api.events

import quoi.api.events.core.UnfilteredEvent
import net.minecraft.world.level.chunk.LevelChunk

abstract class WorldEvent {
    abstract class Chunk {
        class Load(val chunk: LevelChunk) : UnfilteredEvent()
    }
    class Change() : UnfilteredEvent()
    abstract class Load {
        class Start() : UnfilteredEvent()
        class End() : UnfilteredEvent() // tab list and everything loaded
    }
}