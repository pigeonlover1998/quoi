package quoi.utils.skyblock.player.container.task

import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import quoi.QuoiMod.mc
import quoi.annotations.Init
import quoi.api.events.KeyEvent
import quoi.api.events.MouseEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.api.events.core.onAsync
import quoi.api.events.core.wait
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.StringUtils.width
import quoi.utils.player
import quoi.utils.random
import quoi.utils.render.DrawContextUtils.drawText
import quoi.utils.scaledHeight
import quoi.utils.scaledWidth
import quoi.utils.skyblock.player.MovementUtils.stop

/**
 * Manages execution of [ContainerTask]s
 */
@Init // todo prevent client desyncs, impl fastmode thing from jcnlk's fork
object ContainerManager : EventListener {
    var activeTask: ContainerTask? = null
        private set

    private val shouldStopMovement: Boolean
        get() = activeTask?.stopsMovement == true

    init {

        onAsync<TickEvent.Start>(priority = Priority.HIGHEST) {
            val task = activeTask ?: return@onAsync
            val settings = task.settings

            wait(settings.startDelay.random())

            var openedInventory = false

            for (action in task.actions) {
                if (activeTask !== task) return@onAsync

                // hypixel is retarded and only needs inventory opened so there's no need for start/end delay before/after clicks like I did before (https://github.com/pigeonlover1998/quoi/blob/41c7197a61b6e2758cf702c09428f01484240406/src/main/kotlin/quoi/utils/skyblock/player/container/task/ContainerManager.kt#L41)
                val container = (action as? ContainerAction.Click)?.target?.inContainer
                val containerAction = action is ContainerAction.AwaitContainer || container == true

                when {
                    // open inventory if the action isn't in container, no container is open and inventory isn't open
                    container != true && !openedInventory && player.containerMenu.containerId == 0 && (settings.silent || mc.screen !is InventoryScreen) -> {
                        mc.setScreen(InventoryScreen(player))
                        openedInventory = true
                    }
                    // close container if next action is container related and we opened inventory
                    containerAction && openedInventory && (settings.silent || mc.screen is InventoryScreen) -> {
                        player.closeContainer()
                        openedInventory = false
                    }
                }

                // apply delay between clicks subtracting ticks already elapsed since the last click
                // this avoids unneeded delays if other actions already consumed part of the delay
                if (!task.force && action is ItemAction) {
                    val delay = settings.clickDelay.random()
                    val remaining = maxOf(0, delay - task.ticksSinceLastClick)
                    wait(remaining)
                }

                val success = with(action) { execute() }

                if (!success) break // if action failed (timeout or other shit) abort
            }

            wait(settings.endDelay.random())

            if (openedInventory && (settings.silent || mc.screen is InventoryScreen)) { // close inventory if it was opened by task
                player.closeContainer()
            }

            task.onComplete?.invoke()
            task.completed = true
            activeTask = null
        }

        on<TickEvent.Start>(priority = Priority.HIGHEST + 1) {
            if (shouldStopMovement) player.stop()
            activeTask?.ticksSinceLastClick++
        }

        on<WorldEvent.Change> {
            activeTask = null
        }

        on<KeyEvent.Press> { if (shouldStopMovement) cancel() }
        on<KeyEvent.Release> { if (shouldStopMovement) cancel() }
        on<MouseEvent.Click> { if (shouldStopMovement) cancel() } // probably don't wanna click regardless idk
        on<MouseEvent.Scroll> { if (shouldStopMovement) cancel() }
        on<MouseEvent.Move> { if (shouldStopMovement) cancel() }

        on<RenderEvent.Overlay> {
            val task = activeTask ?: return@on
            if (task.name.isNullOrBlank()) return@on
            if (task.totalActions <= 0) return@on

            val x = scaledWidth / 2f - task.name.noControlCodes.width() / 2f
            val y = scaledHeight / 2f + 10f
            ctx.drawText(task.name, x, y)
        }
    }

    fun execute(task: ContainerTask): ContainerTask {
        if (activeTask != null && activeTask !== task) return task
        activeTask = task
        return task
    }

    @JvmStatic
    fun onSetScreen(screen: Screen?): Boolean {
        return activeTask?.settings?.silent == true && screen is InventoryScreen
    }

    override val running: Boolean
        get() = super.running && activeTask != null
}