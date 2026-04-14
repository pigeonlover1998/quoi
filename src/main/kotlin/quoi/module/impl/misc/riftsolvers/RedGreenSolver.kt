package quoi.module.impl.misc.riftsolvers

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.utils.*
import quoi.utils.ChatUtils.modMessage
import quoi.utils.WorldUtils.state
import quoi.utils.render.drawFilledBox
import quoi.utils.skyblock.player.interact.AuraManager
import quoi.utils.skyblock.player.PlayerUtils.at
import quoi.utils.skyblock.player.RotationUtils.rotate
import quoi.utils.skyblock.player.MovementUtils.stop

object RedGreenSolver {
    private var isActive = false
    private var currentRoom: Room? = null
    private var currentStep = 0
    private var waitingTicks = 0

    private var interactStage = 0
    private var awaitingSound = false

    private val rooms = listOf(
        Room(
            BlockPos(-228, 42, -107),
            listOf(
                swap(Side.LEFT),
                move(-228, 42, -109),
                swap(),
                move(-230, 42, -107),
            )
        ),
        Room(
            BlockPos(-234, 42, -107),
            listOf(
                swap(Side.LEFT),
                move(-234, 42, -109),

                swap(),
                move(-234, 42, -105),
                swap(),
                move(-236, 42, -109),

                swap(),
                move(-236, 42, -105),
                swap(),
                move(-238, 42, -109),

                swap(),
                move(-238, 42, -105),
                swap(),

                move(-240, 42, -107),
            )
        ),
        Room(
            BlockPos(-243, 42, -107),
            listOf(
                swap(Side.LEFT),

                move(-243, 42, -109), // plate
                swap(),

                move(-248, 42, -108), // button
                interact(-249, 44, -109),

                move(-244, 42, -109), // go up
                awaitSound(),
                move(-244, 46, -107),

                swap(),
                interact(-243, 48, -107), // button 2
                swap(),
                move(-244, 45, -109), // go down

                move(-244, 42, -105), // go up
                awaitSound(),
                move(-245, 46, -105),

                move(-246, 46, -106), // top plate
                move(-246, 46, -107),
                swap(),

                move(-246, 45, -105), // go down
                move(-248, 42, -107)
            )
        ),

        Room(
            BlockPos(-251, 42, -107),
            listOf(
                swap(Side.RIGHT),

                move(-251, 42, -105), // plate
                swap(),

                move(-255, 42, -105), // button
                interact(-259, 43, -105),

                move(-253, 42, -109), // go up
                awaitSound(),
                wait(5),
                move(-253, 46, -108),

                interact(-253, 48, -108), // lever
                move(-253, 45, -109), // go down

                move(-259, 42, -106), // button 2
                interact(-258, 42, -105),

                awaitSound(), // go up
                wait(5),
                move(-255, 46, -105.2),

                move(-255, 46, -107), // plate
                swap(),

                interact(-257, 48, -108), // button 3

                move(-257, 46, -106), // go down
                move(-259, 45, -106),

                move(-252, 42, -109), // go up
                awaitSound(),
                wait(5),
                move(-252, 46, -108),

                interact(-252, 47, -107), // button 4
                swap(),

                move(-252, 45, -109), // go down

                move(-252.5, 42, -105), // go up
                awaitSound(),
                wait(5),
                move(-252, 46, -105),

                move(-253, 45, -105), // go down
                move(-261, 42, -107)
            )
        )
    )

    fun onMouse() = isActive

    fun onScreen() = if (isActive) reset() else null

    fun onRenderWorld(ctx: WorldRenderContext) {
        if (!isActive) {
            rooms.forEach { room ->
                ctx.drawFilledBox(room.start.aabb, Colour.GREEN, depth = true)
            }
            return
        }

        val action = currentRoom?.actions?.getOrNull(currentStep) ?: return
        when (action) {
            is Action.Move ->     ctx.drawFilledBox(action.pos.blockPos.aabb, Colour.GREEN, depth = true)
            is Action.Interact -> ctx.drawFilledBox(action.pos.aabb, Colour.YELLOW, depth = true)
            else -> {}
        }
    }

    fun onTick(player: LocalPlayer, actionsDelay: Int, interactDelay: Int) {
        if (!isActive) {
            rooms.forEach {
                if (player.at(it.start)) {
                    if (++waitingTicks >= 5) start(it)
                    return
                }
            }
            waitingTicks = 0
            return
        }

        if (awaitingSound) return

        if (waitingTicks > 0) {
            waitingTicks--
            if (waitingTicks > 0) return
        }

        val room = currentRoom ?: return reset()
        val action = room.actions.getOrNull(currentStep)

        if (action == null) {
            modMessage("&aCompleted room!")
            reset()
            return
        }

        when (action) {
            is Action.Move -> {
                if (player.distanceToSqr(action.pos.x, player.y, action.pos.z) < 0.2) {
                    player.stop()
                    waitingTicks = actionsDelay
                    currentStep++
                } else {
                    val dir = getDirection(player.eyePosition, action.pos)
                    player.rotate(yaw = dir.yaw)
                    mc.options.keyUp.isDown = true
                    mc.options.keySprint.isDown = true
                }
            }
            is Action.Interact -> {
                if (interactStage == 0) {
                    val dir = getEtherwarpDirection(action.pos)
                        ?: getDirection(player.eyePosition, Vec3.atCenterOf(action.pos))
                    player.rotate(dir)

                    interactStage = 1
                    waitingTicks = interactDelay
                } else {
//                    player.rightClick()
                    if (player.distanceToSqr(action.pos.vec3) < 25) {
                        AuraManager.interactBlock(action.pos)
                    }
                    interactStage = 0
                    waitingTicks = actionsDelay
                    currentStep++
                }
            }
            is Action.Swap -> {
                action.target?.let { target ->
                    if (player.distanceToSqr(target) < 25) {
                        AuraManager.interactBlock(target.blockPos)
                    }
                    waitingTicks = actionsDelay
                }

                currentStep++
            }
            is Action.Wait -> {
                waitingTicks = action.ticks
                currentStep++
            }
            is Action.AwaitSound -> {
                awaitingSound = true
            }
        }
    }

    fun onSound(packet: ClientboundSoundPacket) {
        if (!isActive || !awaitingSound) return

        if (packet.sound == SoundEvents.GENERIC_EXPLODE && packet.pitch == 1.7936507f) {
            awaitingSound = false
            currentStep++
        }
    }

    private fun start(room: Room) {
        isActive = true
        currentRoom = room
        currentStep = 0
        waitingTicks = 0
        interactStage = 0
        awaitingSound = false
        modMessage("&aStarting!")
    }

    private fun reset() {
        isActive = false
        currentRoom = null
        currentStep = 0
        waitingTicks = 0
        interactStage = 0
        awaitingSound = false
        mc.player?.stop()
    }

    private class Room(val start: BlockPos, val actions: List<Action>)

    private enum class Side { LEFT, RIGHT }

    private sealed class Action {
        class Move(val pos: Vec3) : Action()

        class Swap(val side: Side? = null) : Action() {
            val target: Vec3? get() {
                val player = mc.player ?: return null

                val z = when (side) {
                    Side.LEFT -> -104.0
                    Side.RIGHT -> -110.0
                    null -> {
                        val is104 = BlockPos(player.x, player.y, -104.0).state.block == Blocks.RED_STAINED_GLASS
                        if (is104) -104.0 else -110.0
                    }
                }

                val pos = BlockPos(player.x, player.y, z)
                return if (pos.state.block == Blocks.RED_STAINED_GLASS) Vec3.atCenterOf(pos) else null
            }
        }

        class Interact(val pos: BlockPos) : Action()
        class Wait(val ticks: Int) : Action()
        class AwaitSound : Action()
    }

    private fun move(x: Number, y: Number, z: Number): Action.Move {
        val pos =
            if (x is Int && z is Int)
                Vec3.atCenterOf(BlockPos(x, y, z))
            else
                Vec3(x.toDouble(), y.toDouble(), z.toDouble())

        return Action.Move(pos)
    }
    private fun swap(side: Side? = null) = Action.Swap(side)
    private fun interact(x: Int, y: Int, z: Int) = Action.Interact(BlockPos(x, y, z))
    private fun wait(ticks: Int) = Action.Wait(ticks)
    private fun awaitSound() = Action.AwaitSound()
}