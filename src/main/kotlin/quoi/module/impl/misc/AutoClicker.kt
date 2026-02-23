package quoi.module.impl.misc

import quoi.QuoiMod.scope
import quoi.api.events.MouseEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.config.Config
import quoi.module.Module
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.ListSetting
import quoi.module.settings.impl.NumberRangeSetting
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.formattedString
import quoi.utils.skyblock.ItemUtils.skyblockUuid
import quoi.utils.skyblock.player.PlayerUtils.isLookingAtBreakable
import quoi.utils.skyblock.player.PlayerUtils.leftClick
import quoi.utils.skyblock.player.PlayerUtils.rightClick
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// https://github.com/Noamm9/CatgirlAddons/blob/main/src/main/kotlin/catgirlroutes/module/impl/misc/AutoClicker.kt
object AutoClicker: Module(
    "Auto Clicker",
    desc = "A simple auto clicker for both left and right click. Activates when the corresponding key is being held down."
) {
    private val breakBlocks by BooleanSetting("Break blocks", desc = "Allows the player to break blocks.")
//    private val clickInGui by BooleanSetting("Click while in inventory", desc = "Continues to auto click while the player is in inventory.")
    private val favouriteItems by BooleanSetting("Favourite items only")
    private val favLeft by ListSetting("FAVOURITE_ITEMS_LEFT", mutableListOf<String>())
    private val favRight by ListSetting("FAVOURITE_ITEMS_RIGHT", mutableListOf<String>())

    private val leftClick by BooleanSetting("Left Click", desc = "Toggles the auto clicker for left click.")
    private val leftCps by NumberRangeSetting("Left CPS", 10 to 12, 1, 20).withDependency { leftClick }

    private val rightClick by BooleanSetting("Right Click", desc = "Toggles the auto clicker for right click.")
    private val rightCps by NumberRangeSetting("Right CPS", 10 to 12, 1, 20).withDependency { rightClick }

    private var leftJob: Job? = null
    private var rightJob: Job? = null
    private var lastHeldSlot = -1
    private var isMining = false

    private fun shouldClick(isLeft: Boolean): Boolean {
        if (mc.screen != null) return false
        val favList = if (isLeft) favLeft else favRight
        return !favouriteItems || player.mainHandItem.skyblockUuid in favList
    }

    private val lookingAtBreakable get() = breakBlocks && player.isLookingAtBreakable

    init {
        val ac = command.sub("ac").description("Auto Clicker module settings.")

        ac.sub("add") { button: String ->
            stupid(button) { list ->
                val uuid = player.mainHandItem.skyblockUuid ?: return@stupid modMessage("&cYou are not holding a skyblock item!")
                if (list.contains(uuid)) return@stupid modMessage("&cThis item is already in the $button list!")

                list.add(uuid)
                modMessage("&aAdded ${player.mainHandItem.displayName.formattedString} &ato $button list!")
            }
        }.suggests("button", "left", "right")
        ac.sub("remove") { button: String ->
            stupid(button) { list ->
                val uuid = player.mainHandItem.skyblockUuid ?: return@stupid modMessage("&cYou are not holding a skyblock item!")
                if (!list.remove(uuid)) return@stupid modMessage("&cThis item is not in the $button list!")

                modMessage("&aRemoved ${player.mainHandItem.displayName.formattedString} &afrom $button list!")
            }
        }.suggests("button", "left", "right")
        ac.sub("clear") { button: String ->
            stupid(button) { list ->
                list.clear()
                modMessage("&aCleared the $button list!")
            }
        }.suggests("button", "left", "right")

        on<MouseEvent.Click> {
            if (button !in 0..1) return@on

            val isLeft = button == 0
            val enabled = if (isLeft) leftClick else rightClick

            if (state && enabled && shouldClick(isLeft)) {
                cancel()
                startClicking(isLeft)
            } else {
                stopClicking(isLeft)
            }
        }

        on<TickEvent.End> {
            val currentSlot = player.inventory.selectedSlot
            if (currentSlot != lastHeldSlot || mc.screen != null) {
                reset()
                lastHeldSlot = currentSlot
            }

            if (leftJob != null && lookingAtBreakable) {
                if (!isMining) {
                    mc.options.keyAttack.isDown = true
                    isMining = true
                }
            } else if (isMining) {
                mc.options.keyAttack.isDown = false
                isMining = false
            }
        }

        on<WorldEvent.Change> {
            reset()
        }
    }

    private fun startClicking(isLeft: Boolean) {
        if (isLeft) {
            if (leftJob != null) return
            leftJob = scope.launch { click(true) }
        } else {
            if (rightJob != null) return
            rightJob = scope.launch { click(false) }
        }
    }

    private fun stopClicking(isLeft: Boolean) {
        if (isLeft) {
            leftJob?.cancel()
            leftJob = null
            if (isMining) {
                mc.options.keyAttack.isDown = false
                isMining = false
            }
        } else {
            rightJob?.cancel()
            rightJob = null
        }
    }

    private suspend fun click(isLeft: Boolean) {
        val range = if (isLeft) leftCps else rightCps

        while (true) {
            if (shouldClick(isLeft)) {
                if (isLeft) {
                    if (!lookingAtBreakable) leftClick()
                } else {
                    rightClick()
                }
            }

            delay(Random.nextLong(1000L / range.second, 1000L / range.first))
        }
    }

    private fun stupid(button: String, block: (MutableList<String>) -> Unit) {
        val list = when (button.lowercase()) {
            "left" -> favLeft
            "right" -> favRight
            else -> return modMessage("&cInvalid button! Use \"left\" or \"right\".")
        }
        block(list)
        Config.save()
    }

    private fun reset() {
        stopClicking(true)
        stopClicking(false)
        lastHeldSlot = -1
    }
}