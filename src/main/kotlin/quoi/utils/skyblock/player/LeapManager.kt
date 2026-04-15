package quoi.utils.skyblock.player

import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import quoi.QuoiMod.mc
import quoi.annotations.Init
import quoi.api.events.ChatEvent
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.EventBus.on
import quoi.api.events.core.Priority
import quoi.api.skyblock.dungeon.Dungeon.dungeonTeammatesNoSelf
import quoi.api.skyblock.dungeon.Dungeon.getMageCooldownMultiplier
import quoi.api.skyblock.dungeon.Dungeon.inDungeons
import quoi.api.skyblock.dungeon.DungeonClass
import quoi.api.skyblock.dungeon.DungeonPlayer
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.StringUtils.noControlCodes

@Init
object LeapManager { // still schizophrenia
    private var leapQueue = mutableListOf<String>()
    private var menuOpened = false
    private var inProgress = false

    private var pendingLeap: DungeonPlayer? = null

    var lastLeap = 0L
        private set

    var leapCD = 0.0
        private set

    private val currentLeap get() = leapQueue[0]
    private val inQueue get() = leapQueue.isNotEmpty() // seems useless

    init {
        on<PacketEvent.Received> (Priority.LOWEST) {
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
                        ContainerUtils.click(slot)
                        reloadGui()
                    }
                }
                is ClientboundOpenScreenPacket -> {
                    if (!inQueue) return@on
                    if (!packet.title.string.contains("Leap")) return@on
                    menuOpened = true
                    cancel()
                }
            }
        }

        on<ChatEvent.Packet> {
            if (!inProgress) return@on
            if (message.noControlCodes == "You cannot use this in a solo dungeon!" ||
                message.noControlCodes == "There are no other players to teleport to!") { // probably will never happen on main server
                modMessage("&cFailed to leap! You're in a solo dungeon!")
                reloadGui()
            }
        }

        on<TickEvent.Server> {
            if (leapCD > 0) leapCD -= 1

            if (pendingLeap != null && mc.screen == null && ContainerUtils.containerId == -1) {
                doLeap(pendingLeap!!)
                pendingLeap = null
            }
        }
    }

    fun leap(target: Any) {
        if (!inDungeons || target == DungeonClass.Unknown) return

        val teammate = when (target) {
            is String -> dungeonTeammatesNoSelf.firstOrNull { it.name.equals(target, true) }
            is DungeonClass -> dungeonTeammatesNoSelf.firstOrNull { it.clazz == target }
            else -> null
        } ?: return modMessage("&cFailed to leap! &r$target &cnot found")

//        if (teammate.name !in WorldUtils.players.map { it.profile.name }) return modMessage("&c Failed to leap! &r$target &cnot found")

        if (mc.screen != null || ContainerUtils.containerId != -1) {
            pendingLeap = teammate
            modMessage("&eQueued leap to &f${teammate.name}")
        } else doLeap(teammate)
    }

    private fun doLeap(target: DungeonPlayer) {
        if (inProgress) return
        if (leapCD > 0) {
            modMessage("&cFailed to leap! On cooldown: ${"%.1f".format(leapCD / 20.0)}s")
            return
        }

        inProgress = true
        val r = SwapManager.swapById("INFINITE_SPIRIT_LEAP").success
        scheduleTask {
            if (!r) { inProgress = false; return@scheduleTask }
            PlayerUtils.interact()
            lastLeap = System.currentTimeMillis()
            leapCD = 48 * getMageCooldownMultiplier()
            modMessage("&aLeaping to &r${target.name}")
        }
        leapQueue.add(target.name)
    }

    private fun reloadGui() {
        menuOpened = false
        leapQueue.removeFirst()
        inProgress = false
    }
}