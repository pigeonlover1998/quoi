package quoi.utils.skyblock.player

import quoi.QuoiMod.mc
import quoi.QuoiMod.scope
import quoi.api.commands.QuoiCommand.command
import quoi.api.events.core.EventBus.on
import quoi.api.events.core.EventPriority
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.wait
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.ItemUtils.skyblockId
import kotlinx.coroutines.launch
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.world.item.ItemStack

object SwapManager {
    private var lastKnownServerSlot: Int = -1
    private var hasSwappedThisTick: Boolean = false

    fun init() {
//        command.sub("testzerotick") {
//            scope.launch {
//                modMessage("starting 0 tick test", id = "start0test".hashCode())
//
//                modMessage("1. 0 tick block", prefix = "", id = "0test1".hashCode())
//                val initialSlot = mc.player?.inventory?.selectedSlot ?: 0
//                val target1 = (initialSlot + 1) % 8
//                val target2 = (initialSlot + 2) % 8
//
//                val r1 = swapToSlot(target1)
//                val r2 = swapToSlot(target2)
//
//                if (r1 == SwapResult.SUCCESS && r2 == SwapResult.TOO_FAST) {
//                    modMessage("&aPASS: first allowed, second blocked in same tick", prefix = "", id = "0test1pass".hashCode())
//                } else {
//                    modMessage("&cFAIL: r1=$r1, r2=$r2")
//                }
//                wait(5)
//
//                modMessage("2. slot recovery", prefix = "", id = "0test2".hashCode())
//                val currentSlot = mc.player?.inventory?.selectedSlot
//                if (currentSlot == target1) {
//                    modMessage("&aPASS: client slot synced with server slot ($target1)", prefix = "", id = "0test2pass".hashCode())
//                } else {
//                    modMessage("&cFAIL: client slot $currentSlot, expected $target1")
//                }
//
//                wait(5)
//
//                modMessage("3. raw", prefix = "", id = "0test3".hashCode())
//                val target3 = (initialSlot + 3) % 8
//                mc.player!!.inventory.selectedSlot = target3
//                mc.connection?.send(ServerboundSetCarriedItemPacket(target3))
//
//                val r3 = swapToSlot((target3 + 1) % 8)
//                if (r3 == SwapResult.TOO_FAST) {
//                    modMessage("&aPASS: raw triggered 0t protection", prefix = "", id = "0test3pass".hashCode())
//                } else {
//                    modMessage("&cFAIL: swaptoslot allowed after raw swap. result: $r3")
//                }
//
//                modMessage("complette. current server slot: §6$lastKnownServerSlot", id = "end0test".hashCode())
//            }
//        }

        on<TickEvent.Start> (EventPriority.HIGHEST) { hasSwappedThisTick = false }

        on<WorldEvent.Change> {
            lastKnownServerSlot = -1
            hasSwappedThisTick = false
        }

        on<PacketEvent.Sent> (EventPriority.HIGHEST) {
            if (packet !is ServerboundSetCarriedItemPacket) return@on
            if (packet.slot == lastKnownServerSlot) {
                cancel()
                return@on
            }

            if (hasSwappedThisTick) {
                cancel()
                if (packet.slot != lastKnownServerSlot) {
                    mc.player?.inventory?.selectedSlot = lastKnownServerSlot
                }
                return@on
            }

            lastKnownServerSlot = packet.slot
            hasSwappedThisTick = true
        }
    }

    fun swapToSlot(slot: Int) = when {
        mc.player == null -> SwapResult.FAILED
        slot !in 0..8 -> SwapResult.FAILED
        mc.player!!.inventory.selectedSlot == slot -> SwapResult.ALREADY_SELECTED
        hasSwappedThisTick -> SwapResult.TOO_FAST
        else -> {
//            modMessage("swapping to $slot")
            mc.player!!.inventory.selectedSlot = slot
            mc.connection?.send(ServerboundSetCarriedItemPacket(slot))
            SwapResult.SUCCESS
        }
    }

    fun swapByName(name: String) = findAndSwap(name) { it.displayName.string.noControlCodes.contains(name, true) }

    fun swapById(vararg skyblockIds: String ) = findAndSwap(*skyblockIds) { stack ->
        val id = stack.skyblockId
        id != null && skyblockIds.any { it.equals(id, true) }
    }

    private inline fun findAndSwap(vararg items: String, predicate: (ItemStack) -> Boolean): SwapResult {
        val p = mc.player ?: return SwapResult.FAILED

        for (i in 0..8) {
            val stack = p.inventory.getItem(i)
            if (stack.isEmpty) continue
            if (predicate(stack)) return swapToSlot(i)
        }

        modMessage("Could not find ${items.joinToString(", ")}")
        return SwapResult.NOT_FOUND
    }
}

enum class SwapResult(
    val success: Boolean,
    val already: Boolean = false
) {
    SUCCESS(true),
    ALREADY_SELECTED(true, true),
    TOO_FAST(false),
    NOT_FOUND(false),
    FAILED(false);
}