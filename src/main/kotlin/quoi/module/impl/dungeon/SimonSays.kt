package quoi.module.impl.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.api.abobaui.dsl.ms
import quoi.api.animations.Animation
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.*
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.invoke
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.ChatUtils.command
import quoi.utils.ChatUtils.modMessage
import quoi.utils.EntityUtils
import quoi.utils.StringUtils
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.WorldUtils.state
import quoi.utils.getDirection
import quoi.utils.render.drawFilledBox
import quoi.utils.skyblock.player.AuraManager
import quoi.utils.skyblock.player.RotationUtils.rotateSmoothly
import kotlin.random.Random

// Kyleen
object SimonSays : Module(
    "Simon Says",
    desc = "Automatically completes Simon says device.",
    area = Island.Dungeon(7)
) {

    private val solver by switch("Solver")
    private val firstCol by colourPicker("First colour", Colour.GREEN.withAlpha(0.5f), allowAlpha = true).childOf(::solver)
    private val secondCol by colourPicker("Second colour", Colour.YELLOW.withAlpha(0.5f), allowAlpha = true).childOf(::solver)
    private val thirdCol by colourPicker("Third colour", Colour.RED.withAlpha(0.5f), allowAlpha = true).childOf(::solver)

    private val auto by switch("Auto")
    private val delay by slider("Delay", 200, 50, 500, 10, "Click delay", unit = "ms").childOf(::auto)
    private val startDelay by slider("Start delay", 125, 50, 200, 1, "Delay between clicks when skipping a button.", unit = "ms").childOf(::auto)
    private val smoothRotate by switch("Smooth rotate").childOf(::auto)
    private val rotateStyle by selector("Style", Animation.Style.Linear).childOf(::smoothRotate)
    private val dontCheck by switch("Faster SS?").childOf(::auto) //idk

    private val announceTime by switch("Announce time", desc = "Announces device completion time in party chat")
    private val forceDevice by switch("Force device")
    private val resetSS by button("Reset") { fullReset() }

    private var lastClickTime = 0L
    private var progress = 0
    private var doneFirst = false
    private var doingSS = false
    private var clicked = false
    private var clicks = ArrayList<BlockPos>()
    private var clickedButton: BlockPos? = null
    private var allButtons = ArrayList<BlockPos>()
    private val startButton = BlockPos(110, 121, 91)
    private val standBox = AABB(108.0, 120.0, 90.0, 115.0, 125.0, 95.0)

    private var startActive = false
    private var startStep = 0
    private var nextActionTime = 0L
    private var lastManualReset = 0L
    private var startTime = 0L

    init {
        on<WorldEvent.Change> {
            fullReset()
        }

        on<ChatEvent.Packet> {
            if (message.noControlCodes == "[BOSS] Goldor: Who dares trespass into my domain?") start()
        }

        on<PacketEvent.Sent, ServerboundUseItemOnPacket> {
            if (startActive || packet.hitResult.blockPos != startButton) return@on

            val isActive = EntityUtils.getEntities<ArmorStand>(standBox) {
                it.distanceTo(player) < 6 && it.displayName?.string?.contains("Device Active") == true
            }.isNotEmpty()
            if (isActive) return@on

            if (System.currentTimeMillis() - lastManualReset > 500) {
                if (auto) {
                    cancel()
                    fullReset()
                    start()
                } else {
                    fullReset()
                    doingSS = true
                    startTime = System.currentTimeMillis()
                }
                lastManualReset = System.currentTimeMillis()
            }
        }

        on<BlockUpdateEvent> {
            if (pos.y !in 120..123 || pos.z !in 92..95) return@on

            if (pos.x == 111 && updated.block == Blocks.SEA_LANTERN) {
                val buttonPos = BlockPos(110, pos.y, pos.z)
                if (clicks.getOrNull(0) == buttonPos) {
                    progress = 0
                    if (smoothRotate && doingSS) player.rotateSmoothly(getDirection(pos.randomVec), duration = delay.ms, style = rotateStyle.selected)
                }

                if (clicks.size == 2 && clicks[0] == buttonPos && !doneFirst) {
                    doneFirst = true
                    clicks.removeFirst()
                    if (allButtons.isNotEmpty()) allButtons.removeFirst()
                }

                if (!clicks.contains(buttonPos)) {
                    progress = 0
                    clicks.add(buttonPos)
                    allButtons.add(buttonPos)
                }
                return@on
            }

            if (pos.x == 110) {
                if (updated.block == Blocks.STONE_BUTTON && updated.hasProperty(BlockStateProperties.POWERED) && updated.getValue(BlockStateProperties.POWERED)) {
                    val i = clicks.indexOf(pos)
                    if (i != -1) {
                        progress = i + 1
                    }
                }
            }
        }

        on<TickEvent.Start> {
            if (startActive) {
                if (!auto) {
                    doingSS = true
                    startTime = System.currentTimeMillis()
                    startActive = false
                } else if (System.currentTimeMillis() >= nextActionTime) {
                    when (startStep) {
                        0, 1 -> {
                            reset()
                            clickButton(startButton)

                            val waitMs = Random.nextInt(startDelay, (startDelay * 1.136).toInt())
                            nextActionTime = System.currentTimeMillis() + waitMs
                            startStep++
                        }
                        2 -> {
                            clickButton(startButton)
                            doingSS = true
                            startTime = System.currentTimeMillis()
                            startActive = false
                        }
                    }
                }
                return@on
            }

            if (!doingSS || System.currentTimeMillis() - lastClickTime < delay) return@on
            if (player.distanceToSqr(startButton.center) > 25) return@on

            val canClick = BlockPos(110, 123, 92).state.block == Blocks.STONE_BUTTON

            if (canClick || ((dontCheck && auto) && doneFirst)) {

                if (!doneFirst && clicks.size == 3) {
                    clicks.removeAt(0)
                    if (allButtons.isNotEmpty()) allButtons.removeAt(0)
                }

                doneFirst = true

                if (auto) clicks.getOrNull(progress)?.let { nextPos ->
                    if (nextPos.state.block == Blocks.STONE_BUTTON) {
                        clickButton(nextPos)
                        progress++
                    }
                }
            }
        }

        on<RenderEvent.World> {
            if (player.distanceToSqr(startButton.center) > 1600) return@on

            if (doingSS) {
                var finished = false
                var active = forceDevice
                EntityUtils.getEntities<ArmorStand>(standBox) { it.distanceTo(player) < 6 }.forEach {
                    val name = it.displayName?.string ?: return@forEach
                    if ("Device Active" in name) finished = true
                    if ("Device" in name) active = true
                }

                if (finished) {
                    val time = StringUtils.formatTime(System.currentTimeMillis() - startTime)
                    modMessage("Simon Says took $time")
                    if (announceTime) command("pc Simon Says took $time")
                    fullReset()
                } else if (!active) fullReset()

                if (System.currentTimeMillis() - lastClickTime > delay) clickedButton = null
            }

            if (!solver || !doingSS) return@on

            for (i in progress until clicks.size) {
                val pos = clicks[i]
                val col = when (i - progress) {
                    0 -> firstCol
                    1 -> secondCol
                    else -> thirdCol
                }

                val box = AABB(
                    pos.x + 0.875, pos.y + 0.375, pos.z + 0.3125,
                    pos.x + 1.0, pos.y + 0.625, pos.z + 0.6875
                )
                ctx.drawFilledBox(box, col)
            }
        }
    }

    private fun fullReset() {
        startActive = false
        reset()
    }

    private fun reset() {
        allButtons.clear()
        clicks.clear()
        progress = 0
        doneFirst = false
        doingSS = false
        clicked = false
        startTime = 0L
    }

    private fun start() {
        if (Dungeon.isDead) return
        if (player.distanceToSqr(startButton.center) > 25) return

        if (!startActive && !doingSS) {
            reset()
            clicked = true

            startActive = true
            startStep = 0
            nextActionTime = System.currentTimeMillis()
        }
    }

    private fun clickButton(pos: BlockPos) {
        if (Dungeon.isDead || player.distanceToSqr(pos.center) > 25) return

        lastClickTime = System.currentTimeMillis()

        val shouldSmooth = smoothRotate && (pos != startButton || startStep == 0)

        if (shouldSmooth) {
            player.rotateSmoothly(getDirection(pos.randomVec), duration = delay.ms, style = rotateStyle.selected) {
                if (Dungeon.isDead || player.distanceToSqr(pos.center) > 25) return@rotateSmoothly
                clickedButton = pos
                AuraManager.auraBlock(pos)
                player.swing(InteractionHand.MAIN_HAND)
            }
        } else {
            clickedButton = pos
            AuraManager.auraBlock(pos)
            player.swing(InteractionHand.MAIN_HAND)
        }
    }

    private val BlockPos.randomVec: Vec3 get() {
        val yy = Random.nextDouble(-0.1, 0.1)
        val zz = Random.nextDouble(-0.15, 0.15)
        return Vec3(x + 0.9375, y + 0.5 + yy, z + 0.5 + zz)
    }
}