package quoi.utils.skyblock.player.interact

import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import quoi.QuoiMod.mc
import quoi.utils.connection
import quoi.utils.player
import quoi.utils.skyblock.player.PlayerUtils.eyePosition
import quoi.utils.skyblock.player.interact.AuraManager.debugBox

class EntityInteract(private val entity: Entity, private val action: AuraAction) {
    fun execute() { // todo improve
        if (player.eyePosition().distanceTo(entity.position()) > 5) return

        if (action == AuraAction.INTERACT_AT) {
            val expand = entity.pickRadius.toDouble()
            val boundingBox = entity.boundingBox.inflate(expand)

            val clipResult = boundingBox.clip(player.eyePosition(), boundingBox.center)

            if (clipResult.isPresent) {
                val hitVec = clipResult.get()

                connection.send(
                    ServerboundInteractPacket(
                        entity.id,
                        InteractionHand.MAIN_HAND,
                        hitVec.subtract(entity.position()),
                        player.isShiftKeyDown,
                    )
                )

                connection.send(
                    ServerboundInteractPacket(
                        entity.id,
                        InteractionHand.MAIN_HAND,
                        hitVec.subtract(entity.position()),
                        player.isShiftKeyDown,
                    )
                )

                debugBox(hitVec)
            }
        } else {
            connection.send(
                ServerboundInteractPacket(
                    entity.id,
                    InteractionHand.MAIN_HAND,
                    entity.boundingBox.center.subtract(entity.position()),
                    player.isShiftKeyDown,
                )
            )
            debugBox(entity.boundingBox.center)
        }
    }
}