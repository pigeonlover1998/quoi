package quoi.api.events.core

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundPingPacket
import net.minecraft.network.protocol.game.*
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import quoi.QuoiMod
import quoi.QuoiMod.mc
import quoi.api.events.*
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Dungeon.dungeonItemDrops
import quoi.utils.StringUtils.containsOneOf
import quoi.utils.equalsOneOf

/**
 * Bridges fabric's own event hooks and packet handling.
 */
object EventDispatcher {
    var totalTicks = 0
        private set

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

        LevelRenderEvents.END_MAIN.register { if (mc.level != null && mc.player != null) RenderEvent.World(it).post() }

        ClientLifecycleEvents.CLIENT_STARTED.register { GameEvent.Load().post() }
        ClientLifecycleEvents.CLIENT_STOPPING.register { GameEvent.Unload().post() }

        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ ->
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
                !event.cancelled
            }

            ScreenMouseEvents.allowMouseRelease(screen).register { _, click ->
                val event = GuiEvent.Click(screen, click.x, click.y, click.button(), false)
                event.post()
                !event.cancelled
            }
        }

        HudElementRegistry.attachElementBefore(VanillaHudElements.SLEEP, Identifier.fromNamespaceAndPath(QuoiMod.MOD_ID, "quoi_hud")) { ctx, a ->
            if (mc.options.hideGui || mc.level == null || mc.player == null) return@attachElementBefore
            RenderEvent.Overlay(ctx, a).post()
        }
    }

    @JvmStatic
    fun onPacketReceived(packet: Packet<*>): Boolean {
        if (PacketEvent.Received(packet).post()) return true

        return when (packet) {
            is ClientboundPingPacket -> {
                if (packet.id >= 0) return false
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
                if (itemEntity.item.hoverName.string.containsOneOf(dungeonItemDrops, true) && itemEntity.distanceTo(mc.player as Entity) <= 6)
                    DungeonEvent.Secret.Item(itemEntity).post()
                else false
            }
            is ClientboundRemoveEntitiesPacket -> {
                if (mc.player == null || !Dungeon.inClear) return false
                packet.entityIds.forEach { id ->
                    val entity = mc.level?.getEntity(id) as? ItemEntity ?: return@forEach
                    if (entity.item.hoverName.string.containsOneOf(dungeonItemDrops, true) && entity.distanceTo(mc.player as Entity) <= 6)
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
}