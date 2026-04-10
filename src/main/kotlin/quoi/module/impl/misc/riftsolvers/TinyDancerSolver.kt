package quoi.module.impl.misc.riftsolvers

import net.minecraft.client.KeyMapping
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.sounds.SoundEvents
import quoi.QuoiMod.mc
import quoi.utils.ChatUtils
import quoi.utils.Scheduler
import quoi.utils.skyblock.player.MovementUtils.hold
import quoi.utils.skyblock.player.PlayerUtils.leftClick
import quoi.utils.skyblock.player.RotationUtils.pitch
import quoi.utils.skyblock.player.RotationUtils.yaw

object TinyDancerSolver {
    private var beats = 0
    private var isActive = false
    private var currentSegment = 0
    private var currentAction = 0

    private var currentKey: KeyMapping? = null

    private val validPitches = setOf(
        0.523809552192688,
        1.047619104385376,
        0.6984127163887024,
        0.8888888955116272
    )

    private const val COMPLETE_PITCH = 0.7460317611694336

    private val actions = listOf(
        "move", "move", "move", "move", "move",
        "sneak", "stand",
        "sneak", "stand",
        "sneak", "stand",
        "sneak", "stand",
        "sneak jump", "jump", "sneak", "stand",
        "sneak jump", "jump", "sneak", "stand",
        "sneak jump", "jump", "sneak", "stand",
        "sneak jump", "jump", "sneak", "stand",
        "sneak jump", "jump", "sneak", "stand",
        "sneak jump punch", "jump punch", "sneak punch", "punch",
        "sneak jump punch", "jump punch", "sneak punch", "punch",
        "sneak jump punch", "jump punch", "sneak punch", "punch",
        "sneak jump punch", "jump punch", "sneak punch", "punch"
    )

    private val segments = listOf(
        Segment("z", -105.0) { mc.options.keyLeft },
        Segment("x", -262.0) { mc.options.keyDown },
        Segment("z", -107.0) { mc.options.keyRight },
        Segment("x", -264.0) { mc.options.keyUp }
    )

    fun onMouse() = isActive

    fun onScreen() = if (isActive) reset() else null

    fun onSubTitle(packet: ClientboundSetSubtitleTextPacket) {
        if (!isActive && packet.text.string.contains("Move!")) start()
    }

    fun onSound(packet: ClientboundSoundPacket) {
        if (!isActive) return
        val pitch = packet.pitch.toDouble()
        if (packet.sound == SoundEvents.NOTE_BLOCK_BASS && packet.volume == 1.0f) {
            when (pitch) {
                in validPitches -> {
                    beats++
                    if (beats % 2 == 1) {
                        setDirection(segments[currentSegment].key())
                    }
                }

                COMPLETE_PITCH -> {
                    ChatUtils.modMessage("§aCompleted!")
                    reset()
                }
            }
        }

        if (packet.sound.value() == SoundEvents.PLAYER_BURP) {
            ChatUtils.modMessage("&cFailed!")
            reset()
        }
    }

    fun onTick(player: LocalPlayer, jumpDelay: Int, punchDelay: Int) {
        if (!isActive) return

        if (currentKey == null) return
        val segment = segments[currentSegment]
        val playerPos = if (segment.axis == "x") player.x else player.z


        if (within(segment.target, playerPos)) {
            setDirection(null)

            currentAction++
            val action = actions.getOrNull(currentAction) ?: return reset()
            doAction(player, action, jumpDelay, punchDelay)

            currentSegment = (currentSegment + 1) % segments.size
        }
    }

    private fun doAction(player: LocalPlayer, action: String, jumpDelay: Int, punchDelay: Int) {
        if ("sneak" in action) {
            mc.options.keyShift.hold(10)
        }

        if ("jump" in action) {
            Scheduler.scheduleTask(jumpDelay) {
                mc.options.keyJump.hold(2)
            }
        }

        if ("punch" in action) {
            Scheduler.scheduleTask(punchDelay) {
                player.leftClick()
            }
        }
    }

    private fun setDirection(newKey: KeyMapping?) {
        currentKey?.isDown = false
        newKey?.isDown = true
        currentKey = newKey
    }

    private fun within(target: Double, pos: Double): Boolean {
        return if (target >= 0) {
            pos in target..(target + 1.0)
        } else {
            pos in (target - 1.0)..target
        }
    }

    private fun start() {
        beats = 0
        isActive = true
        currentSegment = 0
        currentAction = 0

        mc.player?.yaw = 90f
        mc.player?.pitch = 90f

        ChatUtils.modMessage("&aStarting!")
    }

    private fun reset() {
        isActive = false

        setDirection(null)

        mc.options.keyShift.isDown = false
        mc.options.keyJump.isDown = false
    }

    private data class Segment(val axis: String, val target: Double, val key: () -> KeyMapping)
}