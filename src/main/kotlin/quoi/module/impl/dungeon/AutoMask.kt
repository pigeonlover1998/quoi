package quoi.module.impl.dungeon

import quoi.QuoiMod.scope
import quoi.api.commands.internal.GreedyString
import quoi.api.events.ChatEvent
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventBus
import quoi.api.events.core.EventPriority
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.DungeonClass
import quoi.module.Module
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.DropdownSetting
import quoi.module.settings.impl.NumberSetting
import quoi.module.settings.impl.SelectorSetting
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.PlayerUtils.interact
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.skyblock.player.SwapResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import kotlin.coroutines.resume

// Kyleen
object AutoMask : Module(
    "Auto Mask",
    desc = "Automatically swaps to invincibility item."
) {

    private val dungeonsOnly by BooleanSetting("Dungeons only")
    private val P3Only by BooleanSetting("Phase 3 only")
    private val stopMoving by BooleanSetting("Prevent moving", true)
    private val antiLoop by BooleanSetting("Anti loop")

    private val phoenix by DropdownSetting("Early enter phoenix/leap").collapsible()
    private val ee3 by BooleanSetting("Rod swap", desc = "Swaps rod and clicks if both masks proc.").withDependency(phoenix)
    private val ee3Delay by NumberSetting("Rod click delay", 2, 0, 10, 1, "Ticks between rod clicks.").withDependency(phoenix) { ee3 }
    private val swapBack by BooleanSetting("Rod swap back", desc = "Swaps back to original slot after rodding.").withDependency(phoenix) { ee3 }
    private val ee3LeapBack by BooleanSetting("Leap back", desc = "Leaps 3s after phoenix proc message.").withDependency(phoenix)
    private val leapClass by SelectorSetting("Leap target class", "Berserk", listOf("Any", "Berserk", "Healer", "Tank", "Mage", "Archer")).withDependency(phoenix) { ee3LeapBack }
    //private val app by BooleanSetting("Use APP", desc = "Uses APP to swap to phoenix and back.").withDependency(phoenix)

    val isSwapping: Boolean get() = _isSwapping
    private var _isSwapping = false

    private var ee3State = EE3State.IDLE
    private var ee3ClickCount = 0
    private var phoenixProcTime = 0L
    private var ee3DelayTimer = 0
    private var originalHotbarSlot = -1

    private enum class EE3State {
        IDLE, SWAP_TO_ROD, CLICK_ROD_INITIAL, WAIT_FOR_PHOENIX, CLICK_ROD_PHOENIX, WAIT_FOR_LEAP, SWAP_TO_LEAP, PERFORM_LEAP
    }

    init {
        command.sub("equip") { maskName: GreedyString ->
            triggerEquip(maskName.string)
        }.description("Automatically swaps to a specified mask.").requires("&cAuto Mask module is disabled!") { enabled }

        on<WorldEvent.Change> {
            resetEE3()
            _isSwapping = false
        }

        on<TickEvent.Start> {
            if (stopMoving && isSwapping && mc.player != null) {
                val opts = mc.options
                val keys = listOf(opts.keyUp, opts.keyDown, opts.keyLeft, opts.keyRight, opts.keyJump, opts.keySprint)
                keys.forEach { it.isDown = false }
            }
        }

        on<TickEvent.End> {
            if (ee3State == EE3State.IDLE) return@on

            val delay = ee3Delay.coerceAtLeast(1)
            ee3DelayTimer++
            if (ee3DelayTimer < delay) return@on

            when (ee3State) {
                EE3State.SWAP_TO_ROD -> {
                    originalHotbarSlot = mc.player?.inventory?.selectedSlot ?: -1
                    val result = SwapManager.swapById("FISHING_ROD", "ROD")

                    if (result == SwapResult.SUCCESS || result == SwapResult.ALREADY_SELECTED) {
                        ee3ClickCount = 0
                        ee3DelayTimer = 0
                        ee3State = EE3State.CLICK_ROD_INITIAL
                    } else if (result == SwapResult.FAILED || result == SwapResult.NOT_FOUND) {
                        resetEE3()
                    }
                }
                EE3State.CLICK_ROD_INITIAL -> {
                    if (ee3ClickCount < 2) {
                        interact()
                        ee3ClickCount++
                        ee3DelayTimer = 0
                    } else {
                        ee3State = EE3State.WAIT_FOR_PHOENIX
                    }
                }
                EE3State.CLICK_ROD_PHOENIX -> {
                    if (ee3ClickCount < 2) {
                        interact()
                        ee3ClickCount++
                        ee3DelayTimer = 0
                    } else {
                        ee3State = if (ee3LeapBack) EE3State.WAIT_FOR_LEAP else {
                            resetEE3(); EE3State.IDLE
                        }
                    }
                }
                EE3State.WAIT_FOR_LEAP -> {
                    if (System.currentTimeMillis() >= phoenixProcTime + 3000) {
                        ee3State = EE3State.SWAP_TO_LEAP
                    }
                }
                EE3State.SWAP_TO_LEAP -> {
                    if (Dungeon.currentP3Section == 3) {
                        resetEE3()
                        return@on
                    }

                    val result = SwapManager.swapById("INFINITE_SPIRIT_LEAP", "SPIRIT_LEAP")
                    if (result == SwapResult.SUCCESS || result == SwapResult.ALREADY_SELECTED) {
                        ee3State = EE3State.PERFORM_LEAP
                    } else {
                        ee3State = EE3State.IDLE
                    }
                }
                EE3State.PERFORM_LEAP -> {
                    autoLeap()
                }
                else -> {}
            }
        }

        on<ChatEvent.Packet> {
            val messageRaw = message.noControlCodes

            if (dungeonsOnly && !Dungeon.inDungeons) return@on
            if (P3Only && !Dungeon.inP3) return@on

            if (messageRaw == "Your Phoenix Pet saved you from certain death!") {
                if (ee3State == EE3State.WAIT_FOR_PHOENIX) {
                    phoenixProcTime = System.currentTimeMillis()
                    ee3ClickCount = 0
                    ee3DelayTimer = 0
                    ee3State = EE3State.CLICK_ROD_PHOENIX
                }
                return@on
            }

            val bonzoMsg = messageRaw == "Your Bonzo's Mask saved your life!" || messageRaw == "Your ⚚ Bonzo's Mask saved your life!"
            val spiritMsg = messageRaw == "Second Wind Activated! Your Spirit Mask saved your life!"

            if (bonzoMsg || spiritMsg) {
                if (antiLoop && isSwapping) return@on

                Scheduler.scheduleTask(1) {
                    handleMaskProc(if (bonzoMsg) "bonzo" else "spirit")
                }
            }
        }
    }

    private fun checkEE3Coords(): Boolean {
        val p = mc.player ?: return false
        val pos = p.blockPosition()
        return (pos.x in 1..2) && (pos.y in 108..110) && (pos.z in 103..105)
    }

    private fun handleMaskProc(nextMask: String) {
        val bonzoCooldown = InvincibilityTimer.InvincibilityType.BONZO.currentCooldown
        val spiritCooldown = InvincibilityTimer.InvincibilityType.SPIRIT.currentCooldown

        if (ee3 && Dungeon.inBoss && Dungeon.inP3 && checkEE3Coords() && bonzoCooldown > 0 && spiritCooldown > 0) {
            ee3State = EE3State.SWAP_TO_ROD
            return
        }

        if (nextMask.equals("spirit", ignoreCase = true) && spiritCooldown <= 0) {
            triggerEquip("spirit")
        } else if (nextMask.equals("bonzo", ignoreCase = true) && bonzoCooldown <= 0) {
            triggerEquip("bonzo")
        }
    }

    fun triggerEquip(maskName: String) {
        if (Dungeon.isDead) return
        if (_isSwapping) return

        val player = mc.player ?: return
        val currentHelmet = player.inventory.getItem(39)
        val helmetName = currentHelmet.displayName.string.noControlCodes

        if (helmetName.contains(maskName, ignoreCase = true)) return

        _isSwapping = true
        scope.launch {
            try {
                equipMask(maskName)
            } finally {
                mc.execute {
                    _isSwapping = false
                }
            }
        }
    }

    private suspend fun equipMask(name: String) {
        val success = PlayerUtils.getContainerItemsClick(
            command = "eq",
            container = "Your Equipment and Stats",
            name = name,
            inContainer = false,
            shift = true,
            cancelReopen = true
        )

        if (success) {
            PlayerUtils.closeContainer()
            //modMessage("Equipped $name")
        } else {
            //modMessage("Could not find $name")
        }
    }

    private fun resetEE3() {
        ee3State = EE3State.IDLE
        ee3ClickCount = 0
        if (swapBack && originalHotbarSlot != -1 && mc.player != null) {
            if (mc.player!!.inventory.selectedSlot != originalHotbarSlot) {
                SwapManager.swapToSlot(originalHotbarSlot)
            }
        }
        originalHotbarSlot = -1
    }

    //rip healerfunny :(

    fun startTestLeap() {
        val player = mc.player ?: return
        if (ee3State != EE3State.IDLE) return

        originalHotbarSlot = player.inventory.selectedSlot
        val result = SwapManager.swapById("INFINITE_SPIRIT_LEAP", "SPIRIT_LEAP")
        if (result == SwapResult.FAILED || result == SwapResult.NOT_FOUND) {
            //modMessage("§c[AutoMask] Spirit Leap not found in hotbar!")
            return
        }

        ee3DelayTimer = 0
        ee3State = EE3State.PERFORM_LEAP
    }

    fun autoLeap() {
        if (Dungeon.isDead) return
        if (ee3State != EE3State.PERFORM_LEAP) return
        //ee3State = EE3State.IDLE

        scope.launch {
            try {
                val playerName = mc.player?.name?.string
                //val teammates = Dungeon.dungeonTeammates

                val preferredClass = when (leapClass.selected.lowercase()) {
                    "berserk" -> DungeonClass.Berserk
                    "healer" -> DungeonClass.Healer
                    "tank" -> DungeonClass.Tank
                    "mage" -> DungeonClass.Mage
                    "archer" -> DungeonClass.Archer
                    else -> null
                }

                val targetName: String? = Dungeon.dungeonTeammates
                    .firstOrNull { preferredClass != null && it.clazz == preferredClass && !it.isDead && it.name != playerName }
                    ?.name
                    ?: Dungeon.dungeonTeammates
                        .firstOrNull { !it.isDead && it.name != playerName }
                        ?.name

//                val targetName = teammates.firstOrNull {
//                    it.clazz == DungeonClass.Berserk && !it.isDead && it.name != playerName
//                }?.name ?: teammates.firstOrNull {
//                    !it.isDead && it.name != playerName
//                }?.name

                if (targetName == null) {
                    modMessage("§c[AutoLeap] No valid teammate found!")
                    return@launch
                }

                val success = LeapHelper.performLeap(targetName)
                if (success) modMessage("§aLeaped to $targetName")
                else modMessage("§cFailed to leap to $targetName (Timeout/Not Found)")
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                resetEE3()
            }
        }
    }

    object LeapHelper {
        suspend fun performLeap(targetName: String): Boolean = suspendCancellableCoroutine { cont ->
            var windowId = -1
            var completed = false

            var openListener: EventBus.EventListener? = null
            var slotListener: EventBus.EventListener? = null

            fun finish(result: Boolean) {
                if (!completed) {
                    completed = true
                    openListener?.remove()
                    slotListener?.remove()
                    mc.execute { cont.resume(result) }
                }
            }

            openListener = EventBus.on<PacketEvent.Received>(EventPriority.LOWEST) {
                if (packet is ClientboundOpenScreenPacket) {
                    val title = packet.title.string.noControlCodes
                    if (title.contains("Spirit Leap", ignoreCase = true)) {
                        windowId = packet.containerId
                        cancel()
                    }
                }
            }

            slotListener = EventBus.on<PacketEvent.Received>(EventPriority.LOWEST) {
                if (packet is ClientboundContainerSetSlotPacket) {
                    if (windowId != -1 && packet.containerId == windowId) {
                        val stack = packet.item

                        if (!stack.isEmpty) {
                            val name = stack.displayName.string.noControlCodes
                            if (name.contains(targetName, ignoreCase = true)) {
                                val slot = packet.slot

                                Scheduler.scheduleTask(1) {
                                    PlayerUtils.click(slot)
                                    Scheduler.scheduleTask(2) {
                                        finish(true)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            /*
            slotListener = EventBus.on<PacketEvent.Received>(EventPriority.LOWEST) {
                if (packet is ClientboundContainerSetSlotPacket && packet.containerId == windowId) {
                    val stack = packet.item
                    if (!stack.isEmpty && stack.displayName.string.noControlCodes.contains(targetName, ignoreCase = true)) {
                        val slot = packet.slot
                        Scheduler.scheduleTask(1) {
                            PlayerUtils.click(slot)
                            Scheduler.scheduleTask(2) {
                                finish(true)
                            }
                        }
                    }
                }
            }
            */

            interact()

            Scheduler.scheduleTask(30) {
                finish(false)
                modMessage("&cidfk cunt") //just in case idk
            }
        }
    }
}