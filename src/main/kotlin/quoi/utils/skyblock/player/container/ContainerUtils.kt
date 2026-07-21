package quoi.utils.skyblock.player.container

import it.unimi.dsi.fastutil.ints.Int2ObjectMaps
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.HashedStack
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import quoi.annotations.Init
import quoi.api.events.PacketEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.api.events.core.until
import quoi.utils.ChatUtils
import quoi.utils.Scheduler
import quoi.utils.Shortcuts
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.items
import quoi.utils.skyblock.item.ItemUtils.loreString
import quoi.utils.skyblock.item.ItemUtils.skyblockUuid
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Init
object ContainerUtils : EventListener, Shortcuts { // todo cleanup
    var containerId = 0
        private set
    var lastStateId = 0
        private set

    var containerServerSide = false
        private set

    private var nextToCancel: String? = null

    init {
        on<PacketEvent.Received>(Priority.HIGHEST + 1) { // more than highest to ensure some ret doesn't cancel it on highest prio
            when (packet) {
                is ClientboundOpenScreenPacket -> {
                    containerId = packet.containerId
                    lastStateId = 0
                    if (nextToCancel != null && packet.title.string.contains(nextToCancel!!, ignoreCase = true)) {
                        nextToCancel = null
                        cancel()
                    }
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

    /**
     * Opens a container via a command and fetches its items into a list.
     *
     * @param command The command to open the container (e.g., "petsmenu").
     * @param containerName The name of the container to open (e.g., "Pets")
     * @param slots The number of slots in the container (default 54).
     * @param timeout Maximum number of ticks to wait for all items (default 20).
     * @return A list of [ItemStack?] representing the container contents.
     *  *         Slots with no item are `null`.
     *  *         Returns an empty list if fetching fails, times out, or container could not be read.
     *
     *  Notes:
     *  - If the container fails to load within [timeout] ticks, returns `emptyList()`.
     *  - You should check for `emptyList()` to detect unsuccessful fetching.
     *  - This function cancels GUI rendering on the client side.
     *  - After fetching items, the container remains open server side. Use [closeContainer] if you want to close the container.
     *    If you want to automatically close
     *    it after fetching, use [getContainerItemsClose] instead.
     */
    suspend fun getContainerItems(command: String, containerName: String, slots: Int = 54, timeout: Int = 20): List<ItemStack?> = // todo remove or replace with containertask
        suspendCoroutine { cont ->
            val items = MutableList<ItemStack?>(slots) { null }
            var windowId: Int? = null
            var complete = false

            ChatUtils.command(command)

            val openSub = until<PacketEvent.Received, ClientboundOpenScreenPacket>(Priority.LOWEST) {
                if (!packet.title.string.contains(containerName, true)) return@until false
                windowId = packet.containerId
                cancel()
                true
            }

            val setSlotSub = until<PacketEvent.Received, ClientboundContainerSetSlotPacket>(Priority.LOWEST) {
                if (packet.containerId != windowId) return@until false
                val slot = packet.slot
                if (slot !in 0..<slots) return@until false
                items[slot] = if (packet.item.isEmpty) null else packet.item

                if (slot == slots - 1) {
                    complete = true
                    cont.resume(items)
                    true
                } else {
                    false
                }
            }

            Scheduler.scheduleTask(timeout) {
                if (!complete) {
                    openSub.unregister()
                    setSlotSub.unregister()
                    ChatUtils.modMessage("&cError: fetching items. timed out")
                    cont.resume(emptyList())
                }
            }
        }

    /**
     * Same as [getContainerItems] but automatically closes the container afterward.
     *
     * @see [quoi.module.impl.misc.PetKeybinds.getPets]
     */
    suspend fun getContainerItemsClose(command: String, containerName: String, slots: Int = 54, timeout: Int = 20): List<ItemStack?> { // todo remove or replace with containertask
        val items = getContainerItems(command, containerName, slots, timeout)
        closeContainer()
        return items
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

    fun click(slot: Int, button: Int = 0, shift: Boolean = false): Boolean { // todo remove
        if (containerId == 0) return false

        val ContainerInput = when {
            button == 2 -> ContainerInput.CLONE
            shift -> ContainerInput.QUICK_MOVE
            else -> ContainerInput.PICKUP
        }

        Scheduler.scheduleTask {
            connection.send(
                ServerboundContainerClickPacket(
                    containerId,
                    lastStateId,
                    slot.toShort(),
                    button.toByte(),
                    ContainerInput,
                    Int2ObjectMaps.emptyMap(),
                    HashedStack.EMPTY
                )
            )
        }
        return true
    }

    fun closeContainer(): Boolean {
        if (containerId == 0) return false
        Scheduler.scheduleTask {
            connection.send(ServerboundContainerClosePacket(containerId))
        }

        return true
    }
}