package quoi.utils.skyblock.player

import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.world.item.ItemStack
import quoi.QuoiMod.mc
import quoi.annotations.Init
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventBus.on
import quoi.api.events.core.Priority
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.ItemUtils.loreString
import quoi.utils.skyblock.ItemUtils.skyblockId

@Init
object SwapManager {
    private var lastKnownServerSlot: Int = 0
    private var lastSentServerSlot: Int = 0
    private var hasSwappedThisTick: Boolean = false
    private var requireSwap: Int = -1
    
    private var swapRequestTick: Int = 0
    private var currentTick: Int = 0

    init {
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

        on<TickEvent.Start> (Priority.HIGHEST) { 
            currentTick++
            hasSwappedThisTick = false
            requireSwap = -1
            swapRequestTick = 0
        }

        on<WorldEvent.Change> {
            lastKnownServerSlot = 0
            lastSentServerSlot = 0
            hasSwappedThisTick = false
            requireSwap = -1
            swapRequestTick = 0
            currentTick = 0
        }

        on<PacketEvent.Sent> (Priority.HIGHEST) {
            if (packet !is ServerboundSetCarriedItemPacket) return@on
            if (packet.slot == lastSentServerSlot) {
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

            val ticksSinceRequest = if (swapRequestTick > 0) currentTick - swapRequestTick else 0
            println("[SwapManager] Swap packet sent after $ticksSinceRequest ticks (slot ${packet.slot}, requested slot: $requireSwap)")

            lastKnownServerSlot = packet.slot
            lastSentServerSlot = packet.slot
            hasSwappedThisTick = true
        }
    }

    fun onHandleLogin() {
        lastKnownServerSlot = 0
        lastSentServerSlot = 0
        hasSwappedThisTick = false
        requireSwap = -1
        swapRequestTick = 0
        currentTick = 0
    }

    fun onEnsureHasSentCarriedItem(managerServerSlot: Int): Boolean {
        val player = mc.player ?: return false
        var i = player.inventory.selectedSlot
        if (!hasSwappedThisTick && requireSwap > -1 && i != requireSwap) {
            if (requireSwap == managerServerSlot) return false
            player.inventory.selectedSlot = requireSwap
            i = requireSwap
        }

        if (i != managerServerSlot && !hasSwappedThisTick) {
            lastKnownServerSlot = i
            return true
        }
        return false
    }

    fun getNextUpdateIndex(): Int {
        if (hasSwappedThisTick) return lastKnownServerSlot
        if (requireSwap > -1) return requireSwap
        return mc.player?.inventory?.selectedSlot ?: 0
    }

    fun canSwap(): Boolean {
        return !hasSwappedThisTick && requireSwap < 0
    }

    fun isDesynced(): Boolean {
        return getNextUpdateIndex() != lastKnownServerSlot
    }

    private fun reserveSwap0(slot: Int): Boolean {
        if (slot !in 0..8) return false
        if (!canSwap()) return slot == getNextUpdateIndex()
        if (swapRequestTick == 0) {
            swapRequestTick = currentTick
            println("[SwapManager] Swap requested at tick $currentTick (slot $slot)")
        }
        requireSwap = slot
        return true
    }

    fun reserveSwap(slot: Int): Boolean {
        if (!reserveSwap0(slot)) return false
        swapToSlot(slot)
        return true
    }

    fun reserveSwapById(vararg skyblockIds: String): Boolean {
        val p = mc.player ?: return false

        if (!canSwap()) {
            val stack = p.inventory.getItem(getNextUpdateIndex())
            val id = stack.skyblockId
            return id != null && skyblockIds.any { it.equals(id, true) }
        }

        for (i in 0..8) {
            val stack = p.inventory.getItem(i)
            if (stack.isEmpty) continue
            val id = stack.skyblockId
            if (id != null && skyblockIds.any { it.equals(id, true) }) {
                if (!reserveSwap0(i)) return false
                swapToSlot(i)
                return true
            }
        }
        return false
    }

    fun checkServerItem(vararg skyblockIds: String): Boolean {
        val p = mc.player ?: return false
        if (lastKnownServerSlot !in 0..8) return false
        val stack = p.inventory.getItem(lastKnownServerSlot)
        val id = stack.skyblockId
        return id != null && skyblockIds.any { it.equals(id, true) }
    }

    fun checkClientItem(vararg skyblockIds: String): Boolean {
        val p = mc.player ?: return false
        val stack = p.inventory.getItem(p.inventory.selectedSlot)
        val id = stack.skyblockId
        return id != null && skyblockIds.any { it.equals(id, true) }
    }

    fun swapToSlot(slot: Int) = when {
        mc.player == null -> SwapResult.FAILED
        slot !in 0..8 -> SwapResult.FAILED
        mc.player!!.inventory.selectedSlot == slot -> SwapResult.ALREADY_SELECTED
        hasSwappedThisTick -> SwapResult.TOO_FAST
        else -> {
            mc.player!!.inventory.selectedSlot = slot
            SwapResult.SUCCESS
        }
    }

    fun swapByName(name: String) = findAndSwap(name) { it.displayName.string.contains(name, true) }

    fun swapByLore(lore: String) = findAndSwap(lore) { it.loreString.noControlCodes.contains(lore, true) }

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
    
    fun sendC07(pos: net.minecraft.core.BlockPos, action: net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action, face: net.minecraft.core.Direction, swing: Boolean, syncSlot: Boolean): Boolean {
        val player = mc.player ?: return false
        val gameMode = mc.gameMode ?: return false
        val level = mc.level ?: return false
        
        if (player.gameMode() == net.minecraft.world.level.GameType.SPECTATOR) return false
        
        if (syncSlot) {
            val slot = player.inventory.selectedSlot
            if (hasSwappedThisTick) return false
        }
        
        if (action == net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK) {
            mc.connection?.send(net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(
                net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                pos,
                net.minecraft.core.Direction.DOWN,
                0
            ))
        } else {
            (gameMode as quoi.mixins.accessors.MultiPlayerGameModeAccessor).invokeStartPrediction(level) { sequence ->
                net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(action, pos, face, sequence)
            }
        }
        
        if (swing) player.swing(net.minecraft.world.InteractionHand.MAIN_HAND)
        return true
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