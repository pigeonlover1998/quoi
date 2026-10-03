package quoi.utils.skyblock.player.container.task

import kotlinx.coroutines.suspendCancellableCoroutine
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import quoi.annotations.Internal
import quoi.utils.skyblock.item.ItemUtils.loreString
import quoi.utils.skyblock.player.container.CONTAINER_ZERO
import quoi.utils.skyblock.player.container.IContainerSettings
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@DslMarker
private annotation class TaskDsl

/**
 * Represents a sequence of [ContainerAction]s to be executed in [ContainerManager]
 */
@OptIn(Internal::class)
class ContainerTask(
    val name: String?,
    val actions: List<ContainerAction>,
    val force: Boolean,
    val settings: IContainerSettings,
    var onComplete: (() -> Unit)?
) {
    var completed = false

    val totalActions = actions.size
    var skippedLast = false
    var ticksSinceLastClick = Int.MAX_VALUE

    // fastblock thing from jcnlk's fork:
    // https://github.com/jcnlk/quoi/blob/546fa869582965e8385b4950b8fb08aa8953f23c/src/main/kotlin/quoi/utils/skyblock/player/container/task/ContainerTask.kt#L69
    // basically lets you move while awaiting for the container. hardcoded cuz I don't really see the reason not to have it :shrug:
    // from 03/10/26: this shit is one month old and Idgaf if it works rn. future me problem TODO
    private var fastBlockActive = false
    private var fastBlockFinished = false
    private var fastClicksRemaining = actions.count { it is ContainerAction.Click }

    /** Starts the one-shot input block. Called when a container or inventory opens. */
    @Internal
    fun beginFastBlock(): Boolean {
        if (fastBlockFinished) return false
        fastBlockActive = true
        return true
    }

    /** Ends the fast block after the last planned click. Called after a click action executes. */
    @Internal
    fun finishFastBlockAfterClick(): Boolean {
        if (!fastBlockActive) return false
        if (fastClicksRemaining > 0) fastClicksRemaining--
        if (fastClicksRemaining > 0) return false

        return finishFastBlock()
    }

    /** Ends the fast block permanently so a later container reopen cannot re-arm it. */
    @Internal
     fun finishFastBlock(): Boolean {
        if (!fastBlockActive) return false
        fastBlockActive = false
        fastBlockFinished = true
        return true
    }

    private val stopsMovement: Boolean get() = run {
        if (!settings.invWalk) true
        else {
            val inventoryClicks = actions.any { it is ContainerAction.Click && it.target.inContainer != true }
            val hasDelay = settings.clickDelay.second > 0 // don't stop if delay is 0

            inventoryClicks && !force && hasDelay // only stop if clicking in inventory, if not forced, and if there's a delay
        }
    }

    val shouldStopMovement: Boolean
        get() = stopsMovement || fastBlockActive

    /**
     * Submits this task to the [ContainerManager] for execution
     */
    fun run(): ContainerTask = ContainerManager.execute(this)
}

@TaskDsl
class ContainerTaskBuilder(val force: Boolean) {
    val actions = mutableListOf<ContainerAction>()
    var onComplete: (() -> Unit)? = null

    private fun click(slot: MenuSlot, button: Int, input: ContainerInput, timeout: Int = 20): ItemAction {
        val action = ContainerAction.Click(slot, button, input, timeout)
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

    // waits for container to potentially reapper and suppresses it. for example when you close wd menu manually after selecting a slot
    // from 03/10/26: this shit is one month old and Idgaf if it works rn. future me problem TODO
    fun catchContainer(
        name: Regex,
        timeout: Int = 20
    ) = actions.add(ContainerAction.CatchContainer(name, timeout))

    fun catchContainer(
        name: String,
        timeout: Int = 20
    ) = catchContainer(Regex(Regex.escape(name), RegexOption.IGNORE_CASE), timeout)

    fun action(block: () -> Unit) = actions.add(ContainerAction.Other(block)) // custom action

    fun wait(ticks: Int) = actions.add(ContainerAction.Wait(ticks)) // wait N ticks

    fun onComplete(callback: () -> Unit) { // cb on task finish
        onComplete = callback
    }

    /**
     * skips the action if the [block] is `true` for the item in the target slot.
     */
    fun <T : ItemAction> T.unless(block: (ItemStack) -> Boolean): T {
        skipIf = block
        return this
    }

    /**
     * skips the action if the item's name contains [text]
     */
    fun <T : ItemAction> T.unlessName(text: String): T = unless { it.displayName.string.contains(text) }

    /**
     * skips the action if the item's lore contains [text]
     */
    fun <T : ItemAction> T.unlessLore(text: String): T = unless { it.loreString?.contains(text) == true }
}

/**
 * @param name optional task name. if not null it will be rendered in the middle of the screen
 * @param force if `true`, bypasses [IContainerSettings.clickDelay] delay
 * @param settings [IContainerSettings] for the task. [CONTAINER_ZERO] by default
 */
@TaskDsl
fun containerTask(
    name: String? = null,
    force: Boolean = false,
    settings: IContainerSettings = CONTAINER_ZERO,
    builder: ContainerTaskBuilder.() -> Unit
): ContainerTask = ContainerTaskBuilder(force).apply(builder).run {
    ContainerTask(name, actions, force, settings, onComplete)
}

/**
 * Submits the task and suspends until it finishes
 */
suspend fun ContainerTask.await(): Unit = suspendCancellableCoroutine { cont ->
    val og = this.onComplete
    this.onComplete = a@ {
        og?.invoke()
        if (!cont.isActive) return@a
        try {
            cont.resume(Unit)
        } catch (e: Throwable) {
            cont.resumeWithException(e)
        }
    }
    this.run()
}

/**
 * Submits the task and suspends until it finishes
 * @return result of [block]
 */
suspend inline fun <T> ContainerTask.await(crossinline block: () -> T): T = suspendCancellableCoroutine { cont ->
    val og = this.onComplete
    this.onComplete = a@ {
        og?.invoke()
        if (!cont.isActive) return@a
        try {
            cont.resume(block())
        } catch (e: Throwable) {
            cont.resumeWithException(e)
        }
    }
    this.run()
}