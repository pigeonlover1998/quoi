package quoi.module.impl.dungeon

import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.decoration.ArmorStand
import quoi.api.events.TickEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.invoke
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.EntityUtils
import quoi.utils.skyblock.player.LeapManager

// Kyleen
object TerminalAura : Module(
    "Terminal Aura",
    desc = "Automatically opens terminals.",
    area = Island.Dungeon(7, inBoss = true)
) {

    private val auraDistance by slider("Distance", 4.0, 0.0, 4.0, 0.1)
    private val auraDelay by slider("Delay", 750, 0, 2000, 50)
    private val groundOnly by switch("Ground only")
    private val leapDelayEnabled by switch("Leap delay", desc = "Delays opening terminals for x seconds after leap")
    private val leapDelay by slider("Leap delay time", 0.5, 0.1, 5.0, 0.1, unit = "s").childOf(::leapDelayEnabled)

    private var lastClick = 0L

    init {

        on<TickEvent.Start> {
            if (!Dungeon.inP3 || Dungeon.inTerminal || Dungeon.isDead || mc.screen != null) return@on
            if (System.currentTimeMillis() - lastClick < auraDelay) return@on

            if (leapDelayEnabled) {
                val delayMs = (leapDelay * 1000.0).toLong()
                if (System.currentTimeMillis() - LeapManager.lastLeap < delayMs) return@on
            }

            if (groundOnly && !player.onGround()) return@on

            val entities = EntityUtils.getEntities<ArmorStand>(player.boundingBox.inflate(auraDistance))

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