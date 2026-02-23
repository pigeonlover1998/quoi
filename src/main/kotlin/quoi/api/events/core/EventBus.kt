package quoi.api.events.core

import quoi.QuoiMod.mc
import quoi.api.events.ChatEvent
import quoi.api.events.DungeonEvent
import quoi.api.events.GameEvent
import quoi.api.events.GuiEvent
import quoi.api.events.PacketEvent
import quoi.api.events.RenderEvent
import quoi.api.events.ServerEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Dungeon.dungeonItemDrops
import quoi.utils.StringUtils.containsOneOf
import quoi.utils.equalsOneOf
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundPingPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import java.util.concurrent.ConcurrentHashMap

// modified zen
object EventBus { // todo cleanup
    val events = ConcurrentHashMap<Class<*>, MutableSet<PrioritisedCallback<*>>>()
    data class PrioritisedCallback<T>(val priority: Int, val callback: T.() -> Unit)
    private var totalTicks = 0

    private var lastWorld: ClientLevel? = null
    private var tabLoaded = false

    init {
        ClientTickEvents.START_CLIENT_TICK.register {
            val world = mc.level
            if (world != lastWorld) {
                if (world != null) WorldEvent.Load.Start().post()
                lastWorld = world
            }

            if (!tabLoaded && !mc.connection?.listedOnlinePlayers.isNullOrEmpty()) {
                tabLoaded = true
                WorldEvent.Load.End().post()
            }
            if (mc.level != null && mc.player != null) TickEvent.Start().post()
        }
        ClientTickEvents.END_CLIENT_TICK.register { if (mc.level != null && mc.player != null) TickEvent.End().post() }

        WorldRenderEvents.END_MAIN.register { if (mc.level != null && mc.player != null) RenderEvent.World(it).post() }

        ClientLifecycleEvents.CLIENT_STARTED.register { GameEvent.Load().post() }
        ClientLifecycleEvents.CLIENT_STOPPING.register { GameEvent.Unload().post() }

        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register { _, _ ->
            WorldEvent.Change().post()
            totalTicks = 0
            tabLoaded = false
            lastWorld = null
        }

        ClientChunkEvents.CHUNK_LOAD.register { _, chunk ->
            if (mc.level != null && mc.player != null) WorldEvent.Chunk.Load(chunk).post()
        }

        ClientPlayConnectionEvents.JOIN.register { handler, _, _ ->
            ServerEvent.Connect(handler.serverData?.ip ?: "SinglePlayer").post()
        }

        ClientPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            ServerEvent.Disconnect(handler.serverData?.ip ?: "SinglePlayer").post()
        }

        ScreenEvents.BEFORE_INIT.register { _, screen, _, _ ->
            ScreenMouseEvents.allowMouseClick(screen).register { _, click ->
                val event = GuiEvent.Click(screen, click.x, click.y, click.button(), true)
                event.post()
                !event.isCancelled()
            }

            ScreenMouseEvents.allowMouseRelease(screen).register { _, click ->
                val event = GuiEvent.Click(screen, click.x, click.y, click.button(), false)
                event.post()
                !event.isCancelled()
            }
        }
    }

    @JvmStatic
    fun onPacketReceived(packet: Packet<*>): Boolean {
        if (PacketEvent.Received(packet).post()) return true

        return when (packet) {
            is ClientboundPingPacket -> {
                totalTicks++
                TickEvent.Server(totalTicks).post()
            }
            is ClientboundSystemChatPacket -> {
                val text = packet.content
                if (packet.overlay) ChatEvent.ActionBar(text.string, text).post() else ChatEvent.Packet(text.string, text).post()
            }
            is ClientboundTakeItemEntityPacket -> {
                if (mc.player == null || !Dungeon.inClear) return false
                val itemEntity = mc.level?.getEntity(packet.itemId) as? ItemEntity ?: return false
                if (itemEntity.item?.hoverName?.string?.containsOneOf(dungeonItemDrops, true) == true && itemEntity.distanceTo(mc.player as Entity) <= 6)
                    DungeonEvent.Secret.Item(itemEntity).post()
                else false
            }
            is ClientboundRemoveEntitiesPacket -> {
                if (mc.player == null || !Dungeon.inClear) return false
                packet.entityIds.forEach { id ->
                    val entity = mc.level?.getEntity(id) as? ItemEntity ?: return@forEach
                    if (entity.item?.hoverName?.string?.containsOneOf(dungeonItemDrops, true) == true && entity.distanceTo(mc.player as Entity) <= 6)
                        DungeonEvent.Secret.Item(entity).post()
                }
                false
            }
            is ClientboundSoundPacket -> {
                if (!Dungeon.inClear) return false
                if (packet.sound.value().equalsOneOf(SoundEvents.BAT_HURT, SoundEvents.BAT_DEATH) && packet.volume == 0.1f)
                    DungeonEvent.Secret.Bat(packet).post()
                else false
            }
            else -> false
        }
    }

    @JvmStatic
    fun onPacketSent(packet: Packet<*>): Boolean {
        if (PacketEvent.Sent(packet).post()) return true

        return when (packet) {
            is ServerboundUseItemOnPacket -> {
                if (!Dungeon.inDungeons || packet.hand == InteractionHand.OFF_HAND) return false
                val pos = packet.hitResult.blockPos
                DungeonEvent.Secret.Interact(pos, mc.level?.getBlockState(pos)?.takeIf { Dungeon.isSecret(it, pos) } ?: return false).post()
                false
            }
            else -> false
        }
    }

//    inline fun <reified T : Any> on(noinline callback: (T) -> Unit): EventListener = on(0, callback, true)
    inline fun <reified T : Event> on(priority: Int = 0, noinline callback: T.() -> Unit): EventListener = on(priority, callback, true)

    inline fun <reified T : Event> on(priority: Int = 0, noinline callback: T.() -> Unit, add: Boolean = true): EventListener {
        val eventClass = T::class.java
        val handlers = events.getOrPut(eventClass) { ConcurrentHashMap.newKeySet() }
        val prioritisedCallback = PrioritisedCallback(priority, callback)
        if (add) handlers.add(prioritisedCallback)
        return EventListenerImpl(prioritisedCallback, handlers)
    }

    @JvmName("onPacket")
    inline fun <reified E, reified P : Packet<*>> on(
        priority: Int = 0,
        noinline callback: PacketScope<E, P>.() -> Unit,
        add: Boolean = true
    ): EventListener where E : Event, E : PacketEvent {
        return on<E>(priority, {
            if (packet is P) callback(PacketScope(this, packet as P))
        }, add)
    }

    fun <T : Event> post(event: T): Boolean {
        val handlers = events[event::class.java] ?: return false
        if (handlers.isEmpty()) return false

        val sortedHandlers = handlers.sortedByDescending { it.priority }

        for (handler in sortedHandlers) {
            if (event is CancellableEvent && event.isCancelled()) {
                return true
            }
            try {
                @Suppress("UNCHECKED_CAST")
                (handler.callback as (T) -> Unit)(event)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return if (event is CancellableEvent) event.isCancelled() else false
    }

    interface EventListener {
        fun remove(): Boolean?
        fun add(): Boolean
    }

    class EventListenerImpl(
        private val callback: PrioritisedCallback<*>,
        private val handlers: MutableSet<PrioritisedCallback<*>>
    ) : EventListener {
        override fun remove(): Boolean = handlers.remove(callback)
        override fun add(): Boolean = handlers.add(callback)
    }
}

class PacketScope<E : PacketEvent, P : Packet<*>>(val event: E, val packet: P) { // idkman
    fun cancel() {
        if (event is CancellableEvent) event.cancel()
    }
}