package quoi.module.impl.dungeon

import quoi.api.events.PacketEvent
import quoi.api.skyblock.Island
import quoi.module.Module
import quoi.utils.StringUtils.noControlCodes
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.world.inventory.MenuType

// Kyleen
object AutoCloseChest : Module(
    "Auto Close Chest",
    area = Island.Dungeon
) {

    private val secretChestTitles = setOf("Chest", "Large Chest", "Trapped Chest")
    private val chestMenuTypes = setOf(
        MenuType.GENERIC_9x1,
        MenuType.GENERIC_9x2,
        MenuType.GENERIC_9x3,
        MenuType.GENERIC_9x4,
        MenuType.GENERIC_9x5,
        MenuType.GENERIC_9x6,
        MenuType.GENERIC_3x3
    )

    init {
        on<PacketEvent.Received, ClientboundOpenScreenPacket> {
            if (packet.type !in chestMenuTypes || packet.title.string.noControlCodes.trim() !in secretChestTitles) return@on

            SecretAura.lastClickedPos?.let { pos ->
                SecretAura.blocksDone.add(pos)
                SecretAura.lastClickedPos = null
            }

            mc.connection?.send(ServerboundContainerClosePacket(packet.containerId))
            cancel()
        }
    }
}