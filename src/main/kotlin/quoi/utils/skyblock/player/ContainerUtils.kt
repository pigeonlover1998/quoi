package quoi.utils.skyblock.player

import it.unimi.dsi.fastutil.ints.Int2ObjectMaps
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.HashedStack
import net.minecraft.network.protocol.game.*
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import quoi.annotations.Init
import quoi.api.events.PacketEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.api.events.core.until
import quoi.module.impl.misc.PetKeybinds
import quoi.utils.ChatUtils
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.Shortcuts
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.items
import quoi.utils.skyblock.item.ItemUtils.loreString
import quoi.utils.skyblock.item.ItemUtils.skyblockUuid
import quoi.utils.skyblock.player.ContainerUtils.closeContainer
import quoi.utils.skyblock.player.ContainerUtils.getContainerItems
import quoi.utils.skyblock.player.ContainerUtils.getContainerItemsClose
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Init
object ContainerUtils : EventListener, Shortcuts { // todo cleanup
    var containerId = -1
        private set
    private var lastStateId = 0

    private var nextToCancel: String? = null

    init {
        on<PacketEvent.Received> (Priority.HIGHEST + 1) { // more than highest to ensure some ret doesn't cancel it on highest prio
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
                    containerId = -1
                    lastStateId = 0
                }
                is ClientboundContainerSetSlotPacket -> {
                    if (packet.containerId == containerId) lastStateId = packet.stateId
                }
            }
        }
        on<PacketEvent.Sent> (Priority.HIGHEST + 1) {
            if (packet is ServerboundContainerClosePacket) {
                containerId = -1
                lastStateId = 0
            }
        }
        on<WorldEvent.Change> {
            containerId = -1
            lastStateId = 0
        }
    }

    /**
     * Fetches items from a container and clicks the one matching the given skyblock UUID (and optional lore string).
     *
     * Only **one** of [uuid] or [name] should be provided.
     *
     * @param command The command to open the container (e.g., "petsmenu").
     * @param container The name of the container to open (e.g., "Pets")
     * @param uuid Optional skyblock UUID of the item to click.
     * @param name Optional display name of the item to click.
     * @param lore Optional lore string to further filter the item.
     * @param inContainer If true, clicks inside the container; if false,clicks in the player inventory.
     * @param slots The number of slots in the container (default 54).
     * @param timeout Maximum number of ticks to wait for all items (default 20).
     * @param button The mouse button to click (default 0 = left click).
     * @param shift Whether to shift-click the item (default false).
     * @param cancelReopen Whether to cancel container reopen (for example, when you swap masks in /eq menu it rebuilds the container)
     * @return `true` if the item was found and clicked successfully, `false` otherwise.
     *
     * Notes:
     *  - If the container fails to load within [timeout] ticks, returns `false`.
     *  - You should check for `false` to detect unsuccessful fetching.
     *  - This function cancels GUI rendering on the client side.
     *  - After fetching items, if the target item isn't found, the container is closed automatically.
     *  - After clicking the item it may not close the GUI server side. Use [closeContainer] if the item you're clicking doesn't close the container automatically.
     *
     *  @see [PetKeybinds.summonPet]
     */
    suspend fun getContainerItemsClick(
        command: String,
        container: String,
        uuid: String? = null,
        name: String? = null,
        lore: String? = null,
        inContainer: Boolean = true,
        slots: Int = 54,
        timeout: Int = 20,
        button: Int = 0,
        shift: Boolean = false,
        cancelReopen: Boolean = false
    ): Boolean {
        require(uuid != null || name != null) { "You must provide either uuid or name." }
        require(!(uuid != null && name != null)) { "Provide only one of uuid or name." }

        val items = getContainerItems(command, container, slots, timeout)
        if (items.isEmpty()) {
            closeContainer()
            return false
        }

        val invItems = player.inventory.items.take(36)
        val finalItems = if (inContainer) items else invItems

        val slot = finalItems.indexOfFirst { item ->
            val matchesUuid = uuid?.let { item.skyblockUuid == it } ?: true
            val matchesName = name?.let { item?.displayName?.string?.noControlCodes?.contains(it, ignoreCase = true) } ?: true
            val matchesLore = lore?.let { item.loreString?.contains(it, ignoreCase = true) == true } ?: true
            matchesUuid && matchesName && matchesLore
        }

        if (slot == -1) {
            closeContainer()
            return false
        }
        val slotToCLick = if (inContainer) slot else {
            if (slot < 9) slots + 27 + slot // hotbar
            else slots + (slot - 9) // inventory
        }
        return if (click(slotToCLick, button, shift)) {
            if (cancelReopen) nextToCancel = container
            true
        } else false
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
    suspend fun getContainerItems(command: String, containerName: String, slots: Int = 54, timeout: Int = 20): List<ItemStack?> = suspendCoroutine { cont ->
        val items = MutableList<ItemStack?>(slots) { null }
        var windowId: Int? = null
        var complete = false

        ChatUtils.command(command)

        val openSub = until<PacketEvent.Received, ClientboundOpenScreenPacket> (Priority.LOWEST) {
            if (!packet.title.string.contains(containerName, true)) return@until false
            windowId = packet.containerId
            cancel()
            true
        }

        val setSlotSub = until<PacketEvent.Received, ClientboundContainerSetSlotPacket> (Priority.LOWEST) {
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

        scheduleTask(timeout) {
            if (!complete) {
                openSub.unregister()
                setSlotSub.unregister()
                modMessage("&cError: fetching items. timed out")
                cont.resume(emptyList())
            }
        }
    }

    /**
     * Same as [getContainerItems] but automatically closes the container afterward.
     *
     * @see [PetKeybinds.getPets]
     */
    suspend fun getContainerItemsClose(command: String, containerName: String, slots: Int = 54, timeout: Int = 20): List<ItemStack?> {
        val items = getContainerItems(command, containerName, slots, timeout)
        closeContainer()
        return items
    }

    fun LocalPlayer.clickSlot(slot: Int, containerId: Int = ContainerUtils.containerId, button: Int = 0, shift: Boolean = false) {
        if (containerId == -1) return

        val clickType = when {
            button == 2 -> ContainerInput.CLONE
            shift -> ContainerInput.QUICK_MOVE
            else -> ContainerInput.PICKUP
        }

        gameMode.handleContainerInput(containerId, slot, button, clickType, this)
    }

    fun click(slot: Int, button: Int = 0, shift: Boolean = false): Boolean {
        if (containerId == -1) return false

        val ContainerInput = when {
            button == 2 -> ContainerInput.CLONE
            shift -> ContainerInput.QUICK_MOVE
            else -> ContainerInput.PICKUP
        }

        scheduleTask {
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
        if (containerId == -1) return false
        scheduleTask {
            connection.send(ServerboundContainerClosePacket(containerId))
        }

        return true
    }
}