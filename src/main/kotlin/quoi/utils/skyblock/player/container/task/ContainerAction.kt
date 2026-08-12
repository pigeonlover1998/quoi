package quoi.utils.skyblock.player.container.task

import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.Priority
import quoi.api.events.core.await
import quoi.api.events.core.wait
import quoi.utils.ChatUtils.modMessage
import quoi.utils.gameMode
import quoi.utils.player
import quoi.utils.skyblock.player.container.ContainerUtils.containerSize
// todo add comments before I forget whatever is going on here
abstract class ItemAction : ContainerAction { // shit that interacts with items/slots
    var skipIf: ((ItemStack) -> Boolean)? = null
}

interface ContainerAction {

    /**
     * Executes the action
     * @return `true` if the action succeeded, `false` to abort the entire task
     */
    suspend fun ContainerManager.execute(): Boolean

    /**
     * Clicks on a specific slot or searches for an item to click
     */
    class Click(
        val target: MenuSlot,
        val button: Int,
        val input: ContainerInput,
        val timeout: Int = 20
    ) : ItemAction() {
        override suspend fun ContainerManager.execute(): Boolean {
            var slot: Int? = null
            var item: ItemStack = ItemStack.EMPTY

            when (target) {
                is IndexSlot -> { // just a slot
                    val menu = player.containerMenu
                    slot = when (target.inContainer) { // calc slot index based on if it's in tnhe container or inventory
                        true, null -> target.index
                        false -> if (menu.containerId == 0) target.index else menu.type.containerSize + (target.index - 9)
                    }
                    item = if (slot in 0 until menu.items.size) menu.items[slot] else ItemStack.EMPTY
                }
                is ItemSlot -> { // item matching a predicate
                    fun check(): Boolean { // check for the item in the menu
                        val menu = player.containerMenu
                        val items = menu.items
                        val size = if (menu.containerId == 0) 0 else menu.type.containerSize

                        val range = when (target.inContainer) { // calc range based on if it's in tnhe container or inventory
                            true -> 0 until size
                            false -> size until items.size
                            null -> 0 until items.size
                        }

                        val s = range.firstOrNull { !items[it].isEmpty && target.predicate(items[it]) }
                        if (s != null) {
                            slot = s
                            item = items[s]
                            return true
                        }
                        return false
                    }

                    if (!check()) { // check instantly. if not found suspend and check every tick til timeout
                        await<TickEvent.Start>(timeout) { check() }
                    }
                }
            }

            if (slot == null) { // no item found during timout
                modMessage("Timed out finding item")
                activeTask?.skippedLast = false
                return false
            }

            val skipped = skipIf?.invoke(item) == true // skip bs
            if (!skipped) {
                gameMode.handleContainerInput(player.containerMenu.containerId, slot, button, input, player)
                activeTask?.ticksSinceLastClick = 0
            }
            activeTask?.skippedLast = skipped

            return true
        }
    }

    /**
     * Executes custom block of code
     */
    class Other(val block: () -> Unit) : ContainerAction {
        override suspend fun ContainerManager.execute(): Boolean {
            block()
            return true
        }
    }

    /**
     * Suspends execution for [ticks]
     */
    class Wait(val ticks: Int) : ContainerAction {
        override suspend fun ContainerManager.execute(): Boolean {
            wait(ticks)
            return true
        }
    }

    /**
     * Waits for a specific container to open and optionally for the items to populate
     */
    class AwaitContainer(
        val containerName: Regex,
        val timeout: Int,
        val waitForItems: Boolean
    ) : ContainerAction {
        override suspend fun ContainerManager.execute(): Boolean {
            // if the previous action was skipped (for example item didn't match the name)
            // the server won't send updated container so we can jsut skip waiting
            val skipped = activeTask?.skippedLast == true

            activeTask?.skippedLast = false

            if (skipped) return true

            var matches = false
            val silent = activeTask?.settings?.silent == true

            val open = await<PacketEvent.Received, ClientboundOpenScreenPacket>( // wait for container to open
                priority = Priority.LOWEST,
                acceptCancelled = true,
                timeout = timeout
            ) {
                matches = containerName.containsMatchIn(packet.title.string)
                if (matches && silent) cancel() // if silent and container matches we cancel
                true
            }

            if (open == null) {
                modMessage("Timed out waiting for container to open")
                return false
            }

            if (!matches) {
                modMessage("Wrong container name. Got &7${open.title.string}&f, needed &7$containerName")
                return false
            }

            if (!waitForItems) return true

            val windowId = open.containerId
            val size = open.type.containerSize

            val items = await<PacketEvent.Received>( // wait for items to populate
                priority = Priority.LOWEST,
                acceptCancelled = true,
                timeout = timeout
            ) {
                when (val p = packet) {
                    is ClientboundContainerSetContentPacket if p.containerId == windowId -> true
                    is ClientboundContainerSetSlotPacket if p.containerId == windowId && p.slot == size - 1 -> true
                    else -> false
                }
            }

            if (items == null) {
                modMessage("Timed out waiting for container items")
                return false
            }

            return true
        }
    }
}