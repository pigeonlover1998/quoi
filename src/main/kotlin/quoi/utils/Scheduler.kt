package quoi.utils

import quoi.QuoiMod.mc
import quoi.api.events.TickEvent
import quoi.api.events.core.EventBus
import kotlinx.coroutines.CompletableDeferred

object Scheduler {
    private val clientTasks = mutableListOf<Task>()
    private val serverTasks = mutableListOf<Task>()

    data class Task(
        var delay: Int,
        val repeat: Int = -1,
        val cb: (Task) -> Unit
    ) {
        fun cancel() {
            clientTasks.remove(this)
            serverTasks.remove(this)
        }
    }

    init {
        EventBus.on<TickEvent.End> {
            tick(clientTasks, server = false)
        }

        EventBus.on<TickEvent.Server> {
            tick(serverTasks, server = true)
        }
    }

    private fun tick(tasks: MutableList<Task>, server: Boolean) {
        for (i in tasks.size - 1 downTo 0) {
            val task = tasks[i]

            if (--task.delay > 0) continue

            if (server) task.cb(task) else mc.submit { task.cb(task) }

            if (task.repeat >= 0) task.delay = task.repeat
            else tasks.removeAt(i)
        }
    }

    @JvmOverloads
    fun scheduleTask(delay: Int = 0, server: Boolean = false, cb: (Task) -> Unit) {
        (if (server) serverTasks else clientTasks).add(Task(delay, cb = cb))
    }

    @JvmOverloads
    fun scheduleLoop(
        interval: Int = 1,
        server: Boolean = false,
        cb: (Task) -> Unit
    ): Task {
        val task = Task(interval, interval, cb)
        (if (server) serverTasks else clientTasks).add(Task(interval, interval, cb))
        return task
    }

    suspend fun wait(ticks: Int = 1, server: Boolean = false) {
        if (ticks <= 0) return

        val deferred = CompletableDeferred<Unit>()

        scheduleTask(ticks, server = server) {
            deferred.complete(Unit)
        }

        deferred.await()
    }
}