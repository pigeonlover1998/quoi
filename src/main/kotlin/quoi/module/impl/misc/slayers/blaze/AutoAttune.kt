package quoi.module.impl.misc.slayers.blaze

import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.world.InteractionHand
import quoi.api.events.ChatEvent
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.api.events.core.once
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.skyblock.player.PlayerUtils.rightClick
import quoi.utils.skyblock.player.SwapManager

object AutoAttune : ToggleableGroup(BlazeSlayer, "Auto attune") {
    private var lastSwap = -1L

    init {
        on<PacketEvent.Sent, ServerboundSwingPacket> {
            if (packet.hand != InteractionHand.MAIN_HAND) return@on
            if (System.currentTimeMillis() - lastSwap < 400L) return@on
            if (BlazeSlayer.attune == null) return@on

            once<TickEvent.Start> {
                val attunement = BlazeSlayer.attune ?: return@once
                val result = SwapManager.swapById(*attunement.daggers)

                if (!result.success) return@once

                if (player.mainHandItem.item != attunement.sword) {
                    player.rightClick()
                }
                lastSwap = System.currentTimeMillis()
            }
        }

        on<ChatEvent.Packet> {
            if (unformatted.startsWith("Strike using the") ||
                unformatted.startsWith("Your hit was reduced by Hellion Shield!")) cancel()
        }
    }
}