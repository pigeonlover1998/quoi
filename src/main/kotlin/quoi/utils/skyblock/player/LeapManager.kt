package quoi.utils.skyblock.player

import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.EventBus.on
import quoi.api.events.core.EventPriority
import quoi.api.skyblock.dungeon.Dungeon.dungeonTeammatesNoSelf
import quoi.api.skyblock.dungeon.Dungeon.getMageCooldownMultiplier
import quoi.api.skyblock.dungeon.Dungeon.inDungeons
import quoi.api.skyblock.dungeon.DungeonClass
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.scheduleTask

object LeapManager { // still schizophrenia
    private var leapQueue = mutableListOf<String>()
    private var menuOpened = false
    private var inProgress = false
    private var clickedLeap = false

    var lastLeap = 0L
        private set

    private var leapCD = 0.0

    private val currentLeap get() = leapQueue[0]
    private val inQueue get() = leapQueue.isNotEmpty()

    fun init() {
        on<PacketEvent.Received> (EventPriority.LOWEST) {
            when (packet) {
                is ClientboundContainerSetSlotPacket -> {
                    if (!inQueue || !menuOpened) return@on

                    val slot = packet.slot
                    val stack = packet.item
                    if (stack.isEmpty) return@on

                    if (slot > 35) {
                        modMessage("§cFailed to leap! §r$currentLeap §cnot found!")
                        reloadGui()
                        return@on
                    }
                    cancel()
                    if (stack.displayName.string.contains(currentLeap)) {
                        PlayerUtils.click(slot)
                        reloadGui()
                    }
                }
                is ClientboundOpenScreenPacket -> {
                    if (!inQueue) return@on
                    if (!packet.title.string.contains("Leap")) return@on
                    menuOpened = true
                    clickedLeap = false
                    cancel()
                }
            }
        }

        on<TickEvent.Server> {
            if (leapCD > 0) leapCD -= 1
        }
    }

    fun leap(target: Any) {
        if (!inDungeons || inProgress || target == DungeonClass.Unknown) return
        if (leapCD > 0) {
            modMessage("&cFailed to leap! On cooldown: ${"%.1f".format(leapCD / 20.0)}s")
            return
        }
        val teammate = when (target) {
            is String -> dungeonTeammatesNoSelf.firstOrNull { it.name == target }
            is DungeonClass -> dungeonTeammatesNoSelf.firstOrNull { it.clazz == target }
            else -> return
        }

        if (teammate != null) {
            inProgress = true
            val r = SwapManager.swapById("INFINITE_SPIRIT_LEAP"/*, "SPIRIT_LEAP"*/).success
            scheduleTask {
                if (!r) return@scheduleTask
                PlayerUtils.interact()
                clickedLeap = true
                lastLeap = System.currentTimeMillis()
                leapCD = 48 * getMageCooldownMultiplier()

                modMessage("&аLeaping to $target")
            }
            leapQueue.add(teammate.name)
        } else {
            inProgress = false
            modMessage("&cFailed to leap! &r$target &cnot found")
        }
    }

    private fun reloadGui() {
        menuOpened = false
        leapQueue.removeFirst()
        inProgress = false
    }
}