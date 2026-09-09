package quoi.utils.skyblock.player.container

import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.MenuType
import quoi.annotations.Init
import quoi.api.events.PacketEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.utils.Shortcuts

@Init
object ContainerUtils : EventListener, Shortcuts { // todo cleanup
    var containerId = 0
        private set
    var lastStateId = 0
        private set

    var containerServerSide = false
        private set

    init {
        on<PacketEvent.Received>(Priority.HIGHEST + 1) { // more than highest to ensure some ret doesn't cancel it on highest prio
            when (packet) {
                is ClientboundOpenScreenPacket -> {
                    containerId = packet.containerId
                    lastStateId = 0
                }
                is ClientboundContainerClosePacket -> {
                    containerId = 0
                    lastStateId = 0
                    containerServerSide = false
                }
                is ClientboundContainerSetSlotPacket -> {
                    if (packet.containerId == containerId) lastStateId = packet.stateId
                }
            }
        }
        on<PacketEvent.Received, ClientboundOpenScreenPacket>(Priority.LOWEST - 1, acceptCancelled = true) {
            if (cancelled) {
                containerServerSide = true
                player.containerMenu = packet.type.create(packet.containerId, player.inventory)
            }
        }
        on<PacketEvent.Sent, ServerboundContainerClosePacket>(Priority.HIGHEST + 1) {
            containerId = 0
            lastStateId = 0
            containerServerSide = false
        }
        on<WorldEvent.Change> {
            containerId = 0
            lastStateId = 0
            containerServerSide = false
        }
    }

    inline val MenuType<*>.containerSize: Int
        get() = when (this) {
            MenuType.GENERIC_9x1 -> 9
            MenuType.GENERIC_9x2 -> 18
            MenuType.GENERIC_9x3 -> 27
            MenuType.GENERIC_9x4 -> 36
            MenuType.GENERIC_9x5 -> 45
            MenuType.GENERIC_9x6 -> 54
            MenuType.GENERIC_3x3 -> 9
            MenuType.CRAFTER_3x3 -> 9
            MenuType.ANVIL -> 3
            MenuType.BEACON -> 1
            MenuType.FURNACE, MenuType.SMOKER, MenuType.BLAST_FURNACE -> 3
            MenuType.SHULKER_BOX -> 27
            else -> 54
        }

    fun LocalPlayer.clickSlot(slot: Int, containerId: Int = ContainerUtils.containerId, button: Int = 0, shift: Boolean = false) {
        if (containerId == 0) return

        val clickType = when {
            button == 2 -> ContainerInput.CLONE
            shift -> ContainerInput.QUICK_MOVE
            else -> ContainerInput.PICKUP
        }

        gameMode.handleContainerInput(containerId, slot, button, clickType, this)
    }
}