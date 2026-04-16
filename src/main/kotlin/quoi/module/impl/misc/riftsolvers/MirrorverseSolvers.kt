package quoi.module.impl.misc.riftsolvers

import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import quoi.api.events.*
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf

object MirrorverseSolvers : Module(
    "Mirrorverse Solvers",
    desc = "Automatically completes Mirrorverse puzzles: Lava Maze, Lava Parkour, Craft Room, Red Green, Tiny Dancer, and Tubulator.",
    subarea = "Mirrorverse"
) {
    private val lavaMaze by switch("Lava maze")
    private val parkour by switch("Lava parkour")

    private val craftRoom by switch("Craft room") // maybe make auto some day
    private val craftMobs by switch("Show mobs").childOf(::craftRoom)
    private val craftRecipe by switch("Show recipes").childOf(::craftRoom)

    private val redGreen by switch("Red green")
    private val rgAction by slider("Action delay", 3, 2, 5, 1, unit = "t").childOf(::redGreen)
    private val rgInteract by slider("Interact delay", 0, 0, 5, 1, unit = "t").childOf(::redGreen)

    private val tinyDancer by switch("Tiny dancer")
    private val tinyJump by slider("Jump delay", 2, 0, 20, 1, unit = "t").json("Tiny jump delay").childOf(::tinyDancer)
    private val tinyPunch by slider("Punch delay", 4, 0, 20, 1, unit = "t").childOf(::tinyDancer)

    private val tubulator by switch("Tubulator") // jump boost 3 parkour

    init {
        on<TickEvent.End> {
            if (tinyDancer) TinyDancerSolver.onTick(player, tinyJump, tinyPunch)
            if (lavaMaze)   LavaMazeSolver.onTick(player)
            if (parkour)    ParkourSolver.onTick(player)
            if (tubulator)  TubulatorSolver.onTick(player)
            if (redGreen)   RedGreenSolver.onTick(player, rgAction, rgInteract)
        }

        on<PacketEvent.Received> {
            when (packet) {
                is ClientboundSoundPacket -> {
                    if (tinyDancer) TinyDancerSolver.onSound(packet)
                    if (redGreen)   RedGreenSolver.onSound(packet)
                }
                is ClientboundSetSubtitleTextPacket -> if (tinyDancer) TinyDancerSolver.onSubTitle(packet)
            }
        }

        on<GuiEvent.Open> {
            if (tinyDancer) TinyDancerSolver.onScreen()
            if (lavaMaze)   LavaMazeSolver.onScreen()
            if (parkour)    ParkourSolver.onScreen()
            if (tubulator)  TubulatorSolver.onScreen()
            if (redGreen)   RedGreenSolver.onScreen()
        }

        on<GuiEvent.DrawTooltip> {
            if (craftRoom && craftRecipe) CraftRoomSolver.onContainer(ctx, player)
        }

        on<ChatEvent.Packet> {
            if (lavaMaze) LavaMazeSolver.onMessage(message)
            if (parkour)  ParkourSolver.onMessage(message)
        }

        on<RenderEvent.World> {
            if (lavaMaze)  LavaMazeSolver.onRenderWorld(ctx)
            if (parkour)   ParkourSolver.onRenderWorld(ctx)
            if (tubulator) TubulatorSolver.onRenderWorld(ctx)
            if (redGreen)  RedGreenSolver.onRenderWorld(ctx)
            if (craftRoom && craftMobs) CraftRoomSolver.onRenderWorld(ctx, player)
        }

        on<MouseEvent.Move> {
            val checks = setOf(
                { tinyDancer && TinyDancerSolver.onMouse() },
                { lavaMaze && LavaMazeSolver.onMouse() },
                { parkour && ParkourSolver.onMouse() },
                { tubulator && TubulatorSolver.onMouse() },
                { redGreen && RedGreenSolver.onMouse() }
            )

            if (checks.any { it() }) cancel()
        }
    }
}