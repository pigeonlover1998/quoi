package quoi.utils.skyblock.player.container.task

import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import quoi.api.events.PacketEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.Priority
import quoi.api.events.core.Subscription
import quoi.api.events.core.until
import quoi.utils.ChatUtils.modMessage
import quoi.utils.gameMode
import quoi.utils.player
import quoi.utils.skyblock.player.container.ContainerUtils.containerSize
import quoi.utils.skyblock.player.container.task.ContainerManager.activeTask

interface ContainerAction { // todo cleanup
    val abort: Boolean get() = false

    var skipIf: ((ItemStack) -> Boolean)?

    fun execute(): Boolean

    class Click(val slot: Int, val button: Int, val input: ContainerInput, val inContainer: Boolean?) : ContainerAction {
        override var skipIf: ((ItemStack) -> Boolean)? = null
        override fun execute(): Boolean {
            val menu = player.containerMenu
            val s = when (inContainer) {
                true, null -> slot
                false -> if (menu.containerId == 0) slot
                else menu.type.containerSize + (slot - 9)
            }

            val item = if (s in 0 until menu.items.size) menu.items[s] else ItemStack.EMPTY
            val skipped = skipIf?.invoke(item) == true

            if (!skipped) {
                gameMode.handleContainerInput(menu.containerId, s, button, input, player)
                activeTask?.ticksSinceLastClick = 0
            }
            activeTask?.skippedLast = skipped

            return true
        }
    }

    class DynamicClick(
        val predicate: (ItemStack) -> Boolean,
        val button: Int,
        val input: ContainerInput,
        val inContainer: Boolean?,
        val timeout: Int,
    ) : ContainerAction {
        private var waited = 0
        private var failed = false

        override val abort: Boolean get() = failed
        override var skipIf: ((ItemStack) -> Boolean)? = null
        override fun execute(): Boolean {
            val menu = player.containerMenu
            val items = menu.items
            val size = if (menu.containerId == 0) 0 else menu.type.containerSize

            val range = when (inContainer) {
                true -> 0 until size
                false -> size until items.size
                null -> 0 until items.size
            }

            val slot = range.firstOrNull { !items[it].isEmpty && predicate(items[it]) }

            if (slot != null) {
                val skipped = skipIf?.invoke(items[slot]) == true
                if (!skipped) {
                    gameMode.handleContainerInput(menu.containerId, slot, button, input, player)
                    activeTask?.ticksSinceLastClick = 0
                }
                activeTask?.skippedLast = skipped
                return true
            }

            waited++
            if (waited >= timeout) {
                modMessage("Timed out")
                failed = true
                return true
            }

            return false
        }
    }

    class Other(val block: () -> Unit) : ContainerAction {
        override var skipIf: ((ItemStack) -> Boolean)? = null
        override fun execute(): Boolean {
            activeTask?.skippedLast = false
            block()
            return true
        }
    }

    class Wait(val ticks: Int): ContainerAction {
        private var waited = 0

        override var skipIf: ((ItemStack) -> Boolean)? = null
        override fun execute(): Boolean {
            activeTask?.skippedLast = false
            waited++
            return waited >= ticks
        }
    }

    class AwaitContainer(
        val containerName: Regex,
        val timeout: Int,
        val waitForItems: Boolean
    ) : ContainerAction, EventListener {
        private var started = false
        private var windowId: Int? = null
        private var menuSize: Int = 54
        private var complete = false
        private var failed = false
        private var ticksWaited = 0
        private var openSub: Subscription<*>? = null
        private var slotSub: Subscription<*>? = null
        private var contentSub: Subscription<*>? = null

        override val abort: Boolean get() = failed
        override var skipIf: ((ItemStack) -> Boolean)? = null
        override fun execute(): Boolean {
            if (complete || failed) {
                cleanup()
                return true
            }

            if (!started) {
                started = true

                if (activeTask?.skippedLast == true) {
                    complete = true
                    cleanup()
                    return true
                }

                openSub = until<PacketEvent.Received, ClientboundOpenScreenPacket>(Priority.LOWEST) {
                    if (!containerName.containsMatchIn(packet.title.string)) {
                        modMessage("Wrong container name. Got &7${packet.title.string}&f, needed &7${containerName}")
                        failed = true
                        cleanup()
                        return@until false
                    }
                    windowId = packet.containerId
                    menuSize = packet.type.containerSize

                    cancel()
                    if (!waitForItems) {
                        complete = true
                        cleanup()
                    }
                    true
                }

                if (waitForItems) {
                    slotSub = until<PacketEvent.Received, ClientboundContainerSetSlotPacket>(Priority.LOWEST) {
                        if (windowId == null || packet.containerId != windowId) {
                            modMessage("Window ids don't match 1")
                            return@until false
                        }
                        if (packet.slot == menuSize - 1) {
                            complete = true
                            cleanup()
                            true
                        } else false
                    }

                    contentSub = until<PacketEvent.Received, ClientboundContainerSetContentPacket>(Priority.LOWEST) {
                        if (windowId == null || packet.containerId != windowId) {
                            modMessage("Window ids don't match 2")
                            return@until false
                        }
                        complete = true
                        cleanup()
                        true
                    }
                }
            }

            ticksWaited++
            if (ticksWaited >= timeout) {
                modMessage("Timed out")
                failed = true
                cleanup()
                return true
            }

            return false
        }

        private fun cleanup() {
            openSub?.unregister()
            slotSub?.unregister()
            contentSub?.unregister()
        }
    }
}