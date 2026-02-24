package quoi.module.impl.dungeon

import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.*
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.invoke
import quoi.module.Module
import quoi.module.settings.impl.ActionSetting
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.NumberSetting
import quoi.utils.ChatUtils.command
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.render.drawFilledBox
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.random.Random

// Kyleen
object AutoSS : Module(
    "AutoSS",
    desc = "Automatically completes Simon says device.",
    area = Island.Dungeon(7)
) {
    private val delay by NumberSetting("Delay", 200.0, 50.0, 500.0, 10.0, "AutoSS delay.", unit = "ms")
    private val forceDevice by BooleanSetting("Force device")
    private val resetSS by ActionSetting("Reset SS") { fullReset() }
    private val autoStart by NumberSetting("Autostart delay", 125.0, 50.0, 200.0, 1.0, "Delay between clicks when skipping a button.", unit = "ms")
    private val dontCheck by BooleanSetting("Faster SS?") //idk
    private val disableSolver by BooleanSetting("Disable solver")
    private val startButtonReset by BooleanSetting("Start button reset", desc = "Pressing the SS start button resets autoss.")
    private val announceTime by BooleanSetting("Announce time", desc = "Runs /pc SS Took {time} when finished. (Only works sometimes atm)")

    private var lastClickTime = 0L
    private var progress = 0
    private var doneFirst = false
    private var doingSS = false
    private var clicked = false
    private var clicks = ArrayList<BlockPos>()
    private var clickedButton: BlockPos? = null
    private var allButtons = ArrayList<BlockPos>()
    private val startButton = BlockPos(110, 121, 91)

    private var startSequenceActive = false
    private var startSequenceStep = 0
    private var nextActionTime = 0L
    private var lastManualReset = 0L
    private var startTime = 0L

    init {
        on<WorldEvent.Change> {
            fullReset()
        }

        on<ChatEvent.Packet> {
            mc.player?.distanceToSqr(startButton.center)?.let { if (it > 25) return@on }

            if (message.noControlCodes == "[BOSS] Goldor: Who dares trespass into my domain?") {
                start()
            }
        }

        on<PacketEvent.Sent, ServerboundUseItemOnPacket> {
            if (!startButtonReset) return@on

            if (startSequenceActive) return@on

            if (packet.hitResult.blockPos == startButton) {

                if (System.currentTimeMillis() - lastManualReset > 500) {
                    cancel()
                    fullReset()
                    start()
                    lastManualReset = System.currentTimeMillis()
                }
            }
        }

        on<BlockUpdateEvent> {
            if (!(pos.x == 111 && pos.y in 120..123 && pos.z in 92..95)) return@on
            if (updated.block != Blocks.SEA_LANTERN) return@on
            val buttonPos = BlockPos(110, pos.y, pos.z)
            mc.execute {
                if (clicks.size == 2) {
                    if (clicks[0] == buttonPos && !doneFirst) {
                        doneFirst = true
                        clicks.removeFirst()
                        if (allButtons.isNotEmpty()) allButtons.removeFirst()
                    }
                }

                if (!clicks.contains(buttonPos)) {
                    progress = 0
                    clicks.add(buttonPos)
                    allButtons.add(buttonPos)
                }
            }
        }

        on<TickEvent.Start> {
            if (startSequenceActive) {
                if (System.currentTimeMillis() < nextActionTime) return@on

                when (startSequenceStep) {
                    0, 1 -> {
                        clearGameData()
                        clickButton(startButton)

                        val waitMs = Random.nextInt(autoStart.toInt(), (autoStart * 1.136).toInt())
                        nextActionTime = System.currentTimeMillis() + waitMs
                        startSequenceStep++
                    }
                    2 -> {
                        clickButton(startButton)
                        doingSS = true
                        startTime = System.currentTimeMillis()
                        startSequenceActive = false
                    }
                }
                return@on
            }

            if (!doingSS) return@on
            if (System.currentTimeMillis() - lastClickTime < delay) return@on

            if (player.distanceToSqr(startButton.center) > 25) return@on

            var device = false
            if (forceDevice) {
                device = true
            } else {
                val stands = level.getEntitiesOfClass(ArmorStand::class.java, AABB(108.0, 120.0, 90.0, 115.0, 125.0, 95.0)) {
                    it.distanceTo(player) < 6 && it.displayName?.string?.contains("Device") == true
                }
                if (stands.isNotEmpty()) device = true
            }

            if (!device) {
                if (doingSS) {
                    if (announceTime) {
                        val timeTaken = (System.currentTimeMillis() - startTime) / 1000.0
                        command("pc SS Took ${String.format("%.2f", timeTaken)}s")
                    }
                    fullReset()
                }
                clicked = false
                return@on
            }

            val detectState = level.getBlockState(BlockPos(110, 123, 92)).block

            if (detectState == Blocks.STONE_BUTTON || (dontCheck && doneFirst)) {

                if (!doneFirst && clicks.size == 3) {
                    clicks.removeAt(0)
                    if (allButtons.isNotEmpty()) allButtons.removeAt(0)
                }

                doneFirst = true

                if (progress < clicks.size) {
                    val nextPos = clicks[progress]

                    if (level.getBlockState(nextPos).block == Blocks.STONE_BUTTON) {
                        clickButton(nextPos)
                        progress++
                    }
                }
            }
        }

        on<RenderEvent.World> {
            if (disableSolver || !doingSS) return@on

            if (System.currentTimeMillis() - lastClickTime > delay) {
                clickedButton = null
            }

            if (player.distanceToSqr(startButton.center) < 1600) {
                val buttonsToRender = ArrayList(allButtons)

                buttonsToRender.forEachIndexed { i, pos ->
                    val color = when (i) {
                        0 -> Colour.GREEN.withAlpha(60)
                        1 -> Colour.YELLOW.withAlpha(60)
                        else -> Colour.RED.withAlpha(60)
                    }

                    val box = AABB(
                        pos.x + 0.875, pos.y + 0.375, pos.z + 0.3125,
                        pos.x + 1.0, pos.y + 0.625, pos.z + 0.6875
                    )

                    ctx.drawFilledBox(box, color)
                }
            }
        }
    }

    private fun fullReset() {
        startSequenceActive = false
        clearGameData()
    }

    private fun clearGameData() {
        allButtons.clear()
        clicks.clear()
        progress = 0
        doneFirst = false
        doingSS = false
        clicked = false
        startTime = 0L
    }

    private fun start() {
        if (!Dungeon.isDead) return
        if (player.distanceToSqr(startButton.center) > 25) return

        if (!startSequenceActive && !doingSS) {
            clearGameData()
            clicked = true

            startSequenceActive = true
            startSequenceStep = 0
            nextActionTime = System.currentTimeMillis()
        }
    }

    private fun clickButton(pos: BlockPos) {
        if (!Dungeon.isDead) return
        if (player.distanceToSqr(pos.center) > 25) return

        clickedButton = pos
        lastClickTime = System.currentTimeMillis()

        val hitResult = BlockHitResult(
            Vec3.atCenterOf(pos),
            Direction.WEST,
            pos,
            false
        )

        mc.connection?.send(
            ServerboundUseItemOnPacket(
                InteractionHand.MAIN_HAND,
                hitResult,
                0
            )
        )
        mc.player!!.swing(InteractionHand.MAIN_HAND)
    }
}