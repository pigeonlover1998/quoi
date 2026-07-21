package quoi.utils.skyblock.player.container.task

import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import quoi.utils.skyblock.item.ItemUtils.loreString

@DslMarker
private annotation class TaskDsl

/**
 * Represents a sequence of [ContainerAction]s to be executed in [ContainerManager]
 */
class ContainerTask(
    val name: String?,
    val actions: List<ContainerAction>,
    val force: Boolean,
    val onComplete: (() -> Unit)?
) {
    var pending = true
    var completed = false

    val totalActions = actions.size
    var completedActions = 0
    var skippedLast = false
    var ticksSinceLastClick = 0
    var actionsThisTick = 0

    var awaiting: ContainerAction? = null

    var queue = ArrayDeque(actions)

    /**
     * Submits this task to the [ContainerManager] for execution
     */
    fun run(): ContainerTask = ContainerManager.execute(this)
}

@TaskDsl
class ContainerTaskBuilder(val force: Boolean) {
    val actions = mutableListOf<ContainerAction>()
    var onComplete: (() -> Unit)? = null

    private fun click(slot: MenuSlot, button: Int, input: ContainerInput, timeout: Int = 20): ContainerAction {
        val action = when (slot) {
            is IndexSlot -> ContainerAction.Click(slot.index, button, input, slot.inContainer)
            is ItemSlot -> ContainerAction.DynamicClick(slot.predicate, button, input, slot.inContainer, timeout)
        }
        actions.add(action)
        return action
    }

    fun pickup(slot: MenuSlot, button: Int = 0) = click(slot, button, ContainerInput.PICKUP) // right/left click
    fun pickupAll(slot: MenuSlot) = click(slot, 0, ContainerInput.PICKUP_ALL) // double click

    fun throwOne(slot: MenuSlot) = click(slot, 0, ContainerInput.THROW) // q
    fun throwAll(slot: MenuSlot) = click(slot, 1, ContainerInput.THROW) // ctrl + q

    fun quickMove(target: MenuSlot) = click(target, 0, ContainerInput.QUICK_MOVE) // shift click
    fun swap(target: MenuSlot, hotbarSlot: Int) = click(target, hotbarSlot, ContainerInput.SWAP) // keys 1 to 9

    fun moveSlot(from: MenuSlot, to: MenuSlot, button: Int = 0) { // move from one slot to another
        pickup(from, button)
        pickup(to, button)
    }

    /**
     * Awaits for container to open before proceeding
     * @param name container name to wait for
     * @param waitForItems if `true`, waits for items to fill the container
     * @param timeout time to wait for the container to open (client ticks)
     */
    fun awaitContainer(
        name: Regex,
        waitForItems: Boolean = false,
        timeout: Int = 20
    ) = actions.add(ContainerAction.AwaitContainer(name, timeout, waitForItems))

    fun awaitContainer(
        name: String,
        waitForItems: Boolean = false,
        timeout: Int = 20
    ) = awaitContainer(Regex(Regex.escape(name), RegexOption.IGNORE_CASE), waitForItems, timeout)

    /**
     * Applies [awaitContainer] before each action.
     * Good for actions that will trigger container reopen (pagination, wardrobe swap, etc).
     */
    fun awaitingContainer(
        name: String,
        waitForItems: Boolean = false,
        timeout: Int = 20,
        block: ContainerTaskBuilder.() -> Unit
    ) = awaitingContainer(Regex(Regex.escape(name), RegexOption.IGNORE_CASE), waitForItems, timeout, block)

    fun awaitingContainer(
        name: Regex,
        waitForItems: Boolean = false,
        timeout: Int = 20,
        block: ContainerTaskBuilder.() -> Unit
    ) {
        val nested = ContainerTaskBuilder(force).apply { block() }
        nested.actions.forEach {
            actions.add(ContainerAction.AwaitContainer(name, timeout, waitForItems))
            actions.add(it)
        }
    }

    fun action(block: () -> Unit) = actions.add(ContainerAction.Other(block)) // custom action

    fun wait(ticks: Int) = actions.add(ContainerAction.Wait(ticks)) // wait N ticks

    fun onComplete(callback: () -> Unit) { // cb on task finish
        onComplete = callback
    }

    /**
     * skips the action if the [block] is `true` for the item in the target slot.
     */
    fun <T : ContainerAction> T.unless(block: (ItemStack) -> Boolean): T {
        skipIf = block
        return this
    }

    /**
     * skips the action if the item's name contains [text]
     */
    fun <T : ContainerAction> T.unlessName(text: String): T = unless { it.displayName.string.contains(text) }

    /**
     * skips the action if the item's lore contains [text]
     */
    fun <T : ContainerAction> T.unlessLore(text: String): T = unless { it.loreString?.contains(text) == true }
}

/**
 * @param force if btrue`, bypasses 1 action per tick limit
 */
@TaskDsl
fun containerTask(
    name: String? = null,
    force: Boolean = false,
    builder: ContainerTaskBuilder.() -> Unit
): ContainerTask = ContainerTaskBuilder(force).apply(builder).run {
    ContainerTask(name, actions, force, onComplete)
}