package quoi.utils.skyblock.player.container

import quoi.annotations.Init
import quoi.api.events.KeyEvent
import quoi.api.events.MouseEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.utils.player
import quoi.utils.skyblock.player.MovementUtils.hasMovementInput
import quoi.utils.skyblock.player.MovementUtils.stop

/**
 * Manages execution of [ContainerTask]s
 */
@Init
object ContainerManager : EventListener { // todo server side containers (to replace the shit in container utils), progress bar
    private var activeTask: ContainerTask? = null
    private var queue = ArrayDeque<ContainerAction>()

    private var accepting = false
    private var activeThisTick = false
    private var actionsThisTick = 0

    private var startDelay = 0 // 2 to not get limboed
    private var endDelay = 0 // 3 to not get limboed

    val active: Boolean
        get() = activeTask != null || queue.isNotEmpty() || endDelay > 0

    init {
        on<TickEvent.Start>(priority = Priority.HIGHEST) {
            accepting = true

            if (active) {
                player.stop()
            }

            if (startDelay > 0) {
                startDelay--
                return@on
            }

            if (endDelay > 0) {
                endDelay--
                return@on
            }

            activeTask?.let { doTask(it) }
        }

        on<TickEvent.End>(priority = Priority.LOWEST) {
            activeThisTick = false
            actionsThisTick = 0
            accepting = false
        }

        on<WorldEvent.Change> {
            activeThisTick = false
            actionsThisTick = 0
            accepting = false
            queue.clear()
            activeTask = null
            startDelay = 0
            endDelay = 0
        }

        on<KeyEvent.Press> { if (active) cancel() }
        on<KeyEvent.Release> { if (active) cancel() }
        on<MouseEvent.Click> { if (active) cancel() }
        on<MouseEvent.Scroll> { if (active) cancel() }
        on<MouseEvent.Move> { if (active) cancel() }
    }

    fun execute(task: ContainerTask): ContainerTask {
        if (activeTask != null && activeTask !== task) return task

        player.stop()
        if (player.hasMovementInput) startDelay = 2 // only apply if holding movement keys
        endDelay = 0
        activeTask = task
        queue = ArrayDeque(task.actions)
        return task
    }

    private fun doTask(task: ContainerTask) {
        if (task.pending) {
            activeTask = task
            queue = ArrayDeque(task.actions)
            task.pending = false
        }

        doActions()
    }

    private fun doActions() {
        val active = activeTask ?: return
        val iterator = queue.iterator()

        while (iterator.hasNext()) {
            if (actionsThisTick >= 1 && !active.force) break

            val action = iterator.next()
            action.block()
            actionsThisTick++

            iterator.remove()
        }

        if (queue.isEmpty()) {
            active.completed = true
            active.onComplete?.invoke()
            activeTask = null
            endDelay = 3
        }

        if (actionsThisTick > 0) {
            activeThisTick = true
        }
    }
}