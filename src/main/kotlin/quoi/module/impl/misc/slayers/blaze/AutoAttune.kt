package quoi.module.impl.misc.slayers.blaze

import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import quoi.api.events.ChatEvent
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.api.events.core.once
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.EntityUtils.getEntities
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.player.PlayerUtils.eyePosition
import quoi.utils.skyblock.player.PlayerUtils.rightClick
import quoi.utils.skyblock.player.SwapManager

object AutoAttune : ToggleableGroup(BlazeSlayer, "Auto attune") {
    private var lastSwap = -1L

    init {
        on<PacketEvent.Sent, ServerboundSwingPacket> {
            if (packet.hand != InteractionHand.MAIN_HAND) return@on
            if (System.currentTimeMillis() - lastSwap < 400L) return@on

            val stands = getEntities<ArmorStand>(6.0) { stand ->
                val name = stand.customName?.string?.noControlCodes ?: return@getEntities false
                Attunement.entries.any { name.contains(it.name, true) }
            }

            if (stands.isEmpty()) return@on

            val from = player.eyePosition()
            val to = from.add(player.getViewVector(1.0f).scale(6.0))

            val target = stands.singleOrNull() ?: stands.firstOrNull { stand ->
                stand.boundingBox.move(0.0, -1.0, 0.0).inflate(0.5, 1.0, 0.5)
                    .clip(from, to).isPresent
            } ?: return@on

            val name = target.customName?.string?.noControlCodes ?: return@on
            val attunement = Attunement.entries.firstOrNull { name.startsWith(it.name) } ?: return@on

            once<TickEvent.Start> {
                val result = SwapManager.swapById(*attunement.daggers)

                if (!result.success) return@once

                if (player.mainHandItem.item != attunement.sword) {
                    player.rightClick()
                }
                lastSwap = System.currentTimeMillis()
            }
        }

//        on<PacketEvent.Sent, ServerboundSwingPacket> {
//            if (System.currentTimeMillis() - lastSwap < 400L) return@on
//
//            once<TickEvent.Start> {
//                val result = SwapManager.swapToSlot(1)
//
//                if (result.success) {
//                    player.rightClick()
//                    lastSwap = System.currentTimeMillis()
//                }
//            }
//        }

        on<ChatEvent.Packet> {
            if (unformatted.startsWith("Strike using the") ||
                unformatted.startsWith("Your hit was reduced by Hellion Shield!")) cancel()
        }
    }

    private val aa = arrayOf("FIREDUST_DAGGER", "BURSTFIRE_DAGGER", "HEARTFIRE_DAGGER")
    private val sc = arrayOf("MAWDUST_DAGGER", "BURSTMAW_DAGGER", "HEARTMAW_DAGGER")

    private enum class Attunement(val sword: Item, val daggers: Array<String>) {
        ASHEN(Items.STONE_SWORD, aa),
        AURIC(Items.GOLDEN_SWORD, aa),
        SPIRIT(Items.IRON_SWORD, sc),
        CRYSTAL(Items.DIAMOND_SWORD, sc)
    }
}