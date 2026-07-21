package quoi.utils.skyblock.player.container.task

import quoi.annotations.Init
import quoi.api.events.KeyEvent
import quoi.api.events.MouseEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.StringUtils.width
import quoi.utils.player
import quoi.utils.render.DrawContextUtils.drawText
import quoi.utils.scaledHeight
import quoi.utils.scaledWidth
import quoi.utils.skyblock.player.MovementUtils.hasMovementInput
import quoi.utils.skyblock.player.MovementUtils.stop

/**
 * Manages execution of [ContainerTask]s
 */
@Init
object ContainerManager : EventListener { // todo toggleable invwalk for container clicks, clean up
    var activeTask: ContainerTask? = null
        private set

    private var startDelay = 0 // 2 to not get limboed
    private var endDelay = 0 // 3 to not get limboed

    val active: Boolean
        get() = activeTask != null || endDelay > 0

    init {
        on<TickEvent.Start>(priority = Priority.HIGHEST) {
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
            activeTask?.actionsThisTick = 0
        }

        on<WorldEvent.Change> {
            activeTask = null
            startDelay = 0
            endDelay = 0
        }

        on<KeyEvent.Press> { if (active) cancel() }
        on<KeyEvent.Release> { if (active) cancel() }
        on<MouseEvent.Click> { if (active) cancel() }
        on<MouseEvent.Scroll> { if (active) cancel() }
        on<MouseEvent.Move> { if (active) cancel() }

        on<RenderEvent.Overlay> {
            val task = activeTask ?: return@on
            if (task.totalActions <= 0) return@on

            val progress = task.completedActions.toFloat() / task.totalActions
            val filled = (progress * 10).toInt().coerceIn(0, 10)
            val empty = 10 - filled

            val bar = "[&a${"█".repeat(filled)}&7${"░".repeat(empty)}&r]"

            val x = scaledWidth / 2f - bar.noControlCodes.width() / 2f
            var y = scaledHeight / 2f + 10f

            if (!task.name.isNullOrBlank()) {
                val x = scaledWidth / 2f - task.name.noControlCodes.width() / 2f
                ctx.drawText(task.name, x, y)
                y += 11f
            }

            ctx.drawText(bar, x, y)
        }
    }

    fun execute(task: ContainerTask): ContainerTask {
        if (activeTask != null && activeTask !== task) return task

        val first = task.actions.firstOrNull()
        val f = first is ContainerAction.Click || first is ContainerAction.DynamicClick
        if (player.hasMovementInput && f) { // only apply if holding movement keys and first action is click
            player.stop()
            startDelay = 2
        }
        endDelay = 0

        task.queue = ArrayDeque(task.actions)
        activeTask = task

        return task
    }

    private fun doTask(task: ContainerTask) {
        if (task.pending) {
            activeTask = task
            task.queue = ArrayDeque(task.actions)
            task.pending = false
        }

        doActions()
    }

    private fun doActions() {
        val active = activeTask ?: return

        active.awaiting?.let {
            if (it.execute()) {
                active.completedActions++
                if (it.abort) {
                    activeTask = null
                    endDelay = maxOf(0, 3 - active.ticksSinceLastClick)
                    return
                }
                active.awaiting = null
            }
            else return
        }

        val iterator = active.queue.iterator()

        while (iterator.hasNext()) {
            if (active.actionsThisTick >= 1 && !active.force) break

            val action = iterator.next()
            if (action.execute()) {
                iterator.remove()
                active.actionsThisTick++
                active.completedActions++

                if (action.abort) {
                    activeTask = null
                    endDelay = maxOf(0, 3 - active.ticksSinceLastClick)
                    return
                }
            } else {
                active.awaiting = action
                active.actionsThisTick++
                break
            }
        }

        if (active.queue.isEmpty() && active.awaiting == null) {
            active.completed = true
            active.onComplete?.invoke()
            activeTask = null
            endDelay = maxOf(0, 3 - active.ticksSinceLastClick)
        }
    }
}