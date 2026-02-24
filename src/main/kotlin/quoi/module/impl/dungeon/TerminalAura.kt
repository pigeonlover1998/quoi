package quoi.module.impl.dungeon

import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.invoke
import quoi.module.Module
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.NumberSetting
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.decoration.ArmorStand

// Kyleen
object TerminalAura : Module(
    "Terminal Aura",
    desc = "Automatically opens terminals.",
    area = Island.Dungeon(7, inBoss = true)
) {

    private val auraDistance by NumberSetting("Distance", 4.0, 0.0, 4.0, 0.1)
    private val auraDelay by NumberSetting("Delay", 750, 0, 2000, 50)
    private val groundOnly by BooleanSetting("Ground Only")

    private var inTerminal = false
    private var lastClick = 0L

    init {
        on<PacketEvent.Received> {
            if (packet is ClientboundOpenScreenPacket) {
                inTerminal = true
            }
            if (packet is ClientboundContainerClosePacket) {
                inTerminal = false
            }
        }

        on<PacketEvent.Sent, ServerboundContainerClosePacket> {
            inTerminal = false
        }

        on<TickEvent.Start> {
            if (!Dungeon.inP3 || inTerminal || Dungeon.isDead) return@on
            if (System.currentTimeMillis() - lastClick < auraDelay) return@on

            if (groundOnly && !player.onGround()) return@on

            val entities = level.getEntitiesOfClass(ArmorStand::class.java, player.boundingBox.inflate(auraDistance))

            for (entity in entities) {
                val name = entity.displayName?.string ?: continue
                if (!name.contains("Inactive Terminal")) continue

                if (entity.isRemoved || !entity.isAlive) continue

                val entityCenter = entity.position().add(0.0, entity.bbHeight / 2.0, 0.0)
                if (player.eyePosition.distanceToSqr(entityCenter) > auraDistance * auraDistance) continue

                val eyesPos = player.eyePosition
                val aabb = entity.boundingBox.inflate(0.1)

                val hitResult = aabb.clip(eyesPos, entityCenter)

                if (hitResult.isEmpty) continue

                val hitVec = hitResult.get()

                val packet = ServerboundInteractPacket.createInteractionPacket(
                    entity,
                    player.isShiftKeyDown,
                    InteractionHand.MAIN_HAND,
                    hitVec
                )
                mc.connection?.send(packet)
                player.swing(InteractionHand.MAIN_HAND)

                lastClick = System.currentTimeMillis()
                break
            }
        }
    }
}