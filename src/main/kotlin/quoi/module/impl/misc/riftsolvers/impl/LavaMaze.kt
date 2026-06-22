package quoi.module.impl.misc.riftsolvers.impl

import quoi.api.colour.Colour
import quoi.api.events.ChatEvent
import quoi.api.events.GuiEvent
import quoi.api.events.MouseEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.module.impl.misc.riftsolvers.MirrorverseSolvers
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.BlockPos
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.aabb
import quoi.utils.render.drawFilledBox
import quoi.utils.skyblock.player.MovementUtils.moveTo
import quoi.utils.skyblock.player.MovementUtils.resetInput
import quoi.utils.skyblock.player.PlayerUtils.at
import quoi.utils.skyblock.player.RotationUtils.pitch
import quoi.utils.skyblock.player.RotationUtils.yaw

object LavaMaze : ToggleableGroup(MirrorverseSolvers, "Lava maze") {

    private var active = false
    private var current = 0
    private var waitingTicks = 0

    init {
        on<TickEvent.End> {
            if (player.at(points.last())) {
                reset()
            }
            if (player.at(points[0])) {
                if (++waitingTicks >= 5) start()
            } else {
                waitingTicks = 0
            }
        }

        on<ChatEvent.Packet> {
            if (!active || message.noControlCodes != "EEK! THE LAVA OOFED YOU!") return@on
            modMessage("&cStopping!")
            reset()
        }

        on<RenderEvent.World> {
            if (!active) ctx.drawFilledBox(points[0].aabb, Colour.GREEN, depth = true)
        }

        on<GuiEvent.Open> {
            if (!active) return@on
            reset()
            player.resetInput()
        }

        on<MouseEvent.Move> {
            if (active) cancel()
        }
    }

    private fun start() {
        active = true
        current = 1
        waitingTicks = 0

        player.yaw = 90f
        player.pitch = 90f

        modMessage("&aStarting!")
        player.moveTo(points)
    }

    private fun reset() {
        active = false
        waitingTicks = 0
        current = 0
    }

    private val points = listOf(
        BlockPos(-83.0, 51.0, -112.0), BlockPos(-86.0, 51.0, -112.0), BlockPos(-86.0, 51.0, -115.0),
        BlockPos(-88.0, 51.0, -115.0), BlockPos(-88.0, 51.0, -110.0), BlockPos(-86.0, 51.0, -110.0),
        BlockPos(-86.0, 51.0, -109.0), BlockPos(-84.0, 51.0, -109.0), BlockPos(-84.0, 51.0, -107.0),
        BlockPos(-90.0, 51.0, -107.0), BlockPos(-90.0, 51.0, -108.0), BlockPos(-93.0, 51.0, -108.0),
        BlockPos(-93.0, 51.0, -110.0), BlockPos(-90.0, 51.0, -110.0), BlockPos(-90.0, 51.0, -112.0),
        BlockPos(-92.0, 51.0, -112.0), BlockPos(-92.0, 51.0, -114.0), BlockPos(-90.0, 51.0, -114.0),
        BlockPos(-90.0, 51.0, -116.0), BlockPos(-94.0, 51.0, -116.0), BlockPos(-94.0, 51.0, -114.0),
        BlockPos(-96.0, 51.0, -114.0), BlockPos(-96.0, 51.0, -116.0), BlockPos(-98.0, 51.0, -116.0),
        BlockPos(-98.0, 51.0, -112.0), BlockPos(-96.0, 51.0, -112.0), BlockPos(-96.0, 51.0, -111.0),
        BlockPos(-95.0, 51.0, -111.0), BlockPos(-95.0, 51.0, -108.0), BlockPos(-98.0, 51.0, -108.0),
        BlockPos(-98.0, 51.0, -110.0), BlockPos(-100.0, 51.0, -110.0), BlockPos(-100.0, 51.0, -108.0),
        BlockPos(-102.0, 51.0, -108.0), BlockPos(-102.0, 51.0, -112.0), BlockPos(-100.0, 51.0, -112.0),
        BlockPos(-100.0, 51.0, -116.0), BlockPos(-102.0, 51.0, -116.0), BlockPos(-102.0, 51.0, -114.0),
        BlockPos(-104.0, 51.0, -114.0), BlockPos(-104.0, 51.0, -112.0), BlockPos(-105.0, 51.0, -112.0)
    )
}