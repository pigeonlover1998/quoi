package quoi.utils.skyblock.player.container

import net.minecraft.world.inventory.ContainerInput
import quoi.utils.gameMode
import quoi.utils.player

@DslMarker
private annotation class ContainerTaskDsl

/**
 * Represents a sequence of [ContainerAction]s to be executed in [ContainerManager]
 */
class ContainerTask(
    val actions: List<ContainerAction>,
    val force: Boolean,
    val onComplete: (() -> Unit)?
) {
    var pending = true
    var completed = false

    /**
     * Submits this task to the [ContainerManager] for execution
     */
    fun run(): ContainerTask = ContainerManager.execute(this)
}

@ContainerTaskDsl
class ContainerTaskBuilder(val force: Boolean) {
    val actions = mutableListOf<ContainerAction>()
    var onComplete: (() -> Unit)? = null

    fun click(slot: Int, button: Int, input: ContainerInput) = actions.add(
        ContainerAction.Click {
            gameMode.handleContainerInput(
                player.containerMenu.containerId,
                slot,
                button,
                input,
                player
            )
        }
    )

    fun pickup(slot: Int, button: Int = 0) = click(slot, button, ContainerInput.PICKUP) // right/left click
    fun pickupAll(slotNum: Int) = click(slotNum, 0, ContainerInput.PICKUP_ALL) // double click

    fun throwOne(slot: Int) = click(slot, 0, ContainerInput.THROW) // q
    fun throwAll(slot: Int) = click(slot, 1, ContainerInput.THROW) // ctrl + q

    fun quickMove(slot: Int) = click(slot, 0, ContainerInput.QUICK_MOVE) // shift click
    fun swap(slot: Int, hotbarSlot: Int) = click(slot, hotbarSlot, ContainerInput.SWAP) // keys 1 to 9

    fun moveSlot(from: Int, to: Int, button: Int = 0) { // move from one slot to another
        pickup(from, button)
        pickup(to, button)
    }

    fun action(block: () -> Unit) = actions.add(ContainerAction.Other(block)) // custom action

    fun onComplete(callback: () -> Unit) { // cb on task finish
        onComplete = callback
    }
}

/**
 * @param force if btrue`, bypasses 1 action per tick limit
 */
@ContainerTaskDsl
fun containerTask(
    force: Boolean = false,
    builder: ContainerTaskBuilder.() -> Unit
): ContainerTask = ContainerTaskBuilder(force).apply(builder).run {
    ContainerTask(actions, force, onComplete)
}