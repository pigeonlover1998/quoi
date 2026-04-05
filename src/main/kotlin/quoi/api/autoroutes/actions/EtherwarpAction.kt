package quoi.api.autoroutes.actions

import kotlinx.coroutines.withTimeoutOrNull
import quoi.QuoiMod.mc
import quoi.api.autoroutes.arguments.AwaitArgument
import quoi.api.skyblock.dungeon.Dungeon.currentRoom
import quoi.config.TypeName
import quoi.module.impl.dungeon.AutoRoutes
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.wait
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.RotationUtils.rotate
import quoi.utils.skyblock.player.SwapManager
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.utils.rayCast
import kotlin.math.atan2
import kotlin.math.sqrt

@TypeName("etherwarp")
class EtherwarpAction(val yaw: Float = 0f, val pitch: Float = 0f, val vec: Vec3? = null) : RingAction {

    override val colour: Colour
        get() = Colour.CYAN

    override suspend fun execute(player: LocalPlayer) {
        val room = currentRoom ?: return
        if (!mc.options.keyShift.isDown) {
            mc.options.keyShift.isDown = true
            wait(2)
        }
        if (!SwapManager.swapById("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END").success) {
            modMessage("Aotv not found retard")
            return
        }
        if (AutoRoutes.zeroTick) {
            val rings = AutoRoutes.routes[room.data.name] ?: return

            val index = rings.indexOfFirst { it.action === this }
            if (index == -1) return

            for (i in index until rings.size) {
                val ring = rings[i]
                val action = ring.action

                if (action !is EtherwarpAction) break

                if (i > index) {
                    if (ring.arguments?.any { it is AwaitArgument } == true || !ring.checkArgs()) {
                        break
                    }
                }

                val realYaw = room.getRealYaw(action.yaw)
                val dx = -kotlin.math.sin(Math.toRadians(realYaw.toDouble()))
                val dz = kotlin.math.cos(Math.toRadians(realYaw.toDouble()))
                val dy = -kotlin.math.sin(Math.toRadians(action.pitch.toDouble()))
                val horizontalDist = sqrt(dx * dx + dz * dz)
                val sneakHeight = 1.54f - atan2(dy, horizontalDist).toFloat()

                player.setPos(player.x, player.y + (sneakHeight - 1.54f), player.z)

                mc.connection!!.send(
                    ServerboundUseItemPacket(
                        InteractionHand.MAIN_HAND,
                        0,
                        realYaw,
                        action.pitch
                    )
                )

                player.setPos(player.x, player.y - (sneakHeight - 1.54f), player.z)

                if (i > index) {
                    AutoRoutes.visitedRings.add(ring)
                }
            }

        } else {
            player.rotate(room.getRealYaw(yaw), pitch)

            AutoRoutes.interactDelay()
            withTimeoutOrNull(500) {
                while (rayCast() == null) {
                    wait(1)
                }
            }
            PlayerUtils.interact()
        }
    }
}