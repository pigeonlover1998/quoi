package quoi.module.impl.dungeon

import kotlinx.coroutines.launch
import quoi.QuoiMod.scope
import quoi.api.commands.internal.GreedyString
import quoi.api.events.ChatEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.dungeon.Dungeon
import quoi.module.Module
import quoi.utils.ChatUtils.command
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.player.ContainerUtils

// Kyleen
object AutoMask : Module(
    "Auto Mask",
    desc = "Automatically swaps to invincibility item."
) {

    private val dungeonsOnly by switch("Dungeons only")
    private val bossOnly by switch("Boss only")
    private val P3Only by switch("Phase 3 only")
    private val stopMoving by switch("Prevent moving", true)
    private val announceProc by switch("Announce mask proc")

    val isSwapping: Boolean get() = _isSwapping
    private var _isSwapping = false

    init {
        command.sub("equip") { maskName: GreedyString ->
            triggerEquip(maskName.string)
        }.description("Automatically swaps to a specified mask.").requires("&cAuto Mask module is disabled!") { enabled }

        on<WorldEvent.Change> {
            _isSwapping = false
        }

        on<TickEvent.Start> {
            if (stopMoving && isSwapping && mc.player != null) {
                val opts = mc.options
                val keys = listOf(opts.keyUp, opts.keyDown, opts.keyLeft, opts.keyRight, opts.keyJump, opts.keySprint)
                keys.forEach { it.isDown = false }
            }
        }

        on<ChatEvent.Packet> {
            val messageRaw = message.noControlCodes

            val bonzoMsg = messageRaw == "Your Bonzo's Mask saved your life!" || messageRaw == "Your ⚚ Bonzo's Mask saved your life!"
            val spiritMsg = messageRaw == "Second Wind Activated! Your Spirit Mask saved your life!"

            if (dungeonsOnly && !Dungeon.inDungeons) return@on
            if (bossOnly && !Dungeon.inBoss) return@on
            if (P3Only && !Dungeon.inP3) return@on

            if (bonzoMsg || spiritMsg) {
                triggerEquip(if (bonzoMsg) "spirit mask" else "bonzo's mask")

                if (announceProc) {
                    if (spiritMsg) command("pc Spirit Procced!")
                    if (bonzoMsg) command("pc Bonzo Procced!")
                }
            }
        }
    }

    fun triggerEquip(maskName: String) {
        if (Dungeon.isDead || _isSwapping || Dungeon.inTerminal) return

        val player = mc.player ?: return

        val currentHelmet = player.inventory.getItem(39)
        val helmetName = currentHelmet.displayName.string

        if (helmetName.contains(maskName, ignoreCase = true)) return

        _isSwapping = true
        scope.launch {
            try {
                equipMask(maskName)
            } finally {
                _isSwapping = false
            }
        }
    }

    private suspend fun equipMask(name: String) {
        val success = ContainerUtils.getContainerItemsClick(
            command = "eq",
            container = "Your Equipment and Stats",
            name = name,
            inContainer = false,
            shift = true,
            cancelReopen = true
        )

        if (success) {
            scheduleTask(2) { ContainerUtils.closeContainer() }
        }
    }
}