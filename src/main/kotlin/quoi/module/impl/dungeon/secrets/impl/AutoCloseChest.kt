package quoi.module.impl.dungeon.secrets.impl

import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.world.inventory.MenuType
import quoi.api.events.PacketEvent
import quoi.api.events.core.on
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.invoke
import quoi.module.impl.dungeon.secrets.Secrets
import quoi.module.settings.group.ToggleableGroup

// Kyleen
object AutoCloseChest : ToggleableGroup(
    Secrets,
    "Auto Close Chest",
    desc = "Automatically closes secret chests.",
    area = Island.Dungeon(inClear = true)
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
            if (packet.type !in chestMenuTypes || packet.title.string.trim() !in secretChestTitles) return@on

            SecretAura.lastClickedPos?.let { pos ->
                SecretAura.blocksDone.add(pos.asLong())
                SecretAura.lastClickedPos = null
            }

            connection.send(ServerboundContainerClosePacket(packet.containerId))
            cancel()
        }
    }
}