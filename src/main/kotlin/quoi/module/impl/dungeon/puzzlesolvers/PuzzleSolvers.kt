package quoi.module.impl.dungeon.puzzlesolvers

import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.*
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon.currentRoom
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomType
import quoi.api.skyblock.invoke
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.Scheduler.scheduleLoop
import quoi.utils.StringUtils.noControlCodes

object PuzzleSolvers : Module(
    "Puzzle Solvers",
    area = Island.Dungeon(inClear = true)
) {
    private val fillDropdown by text("Ice fill")
    private val fillSolver by switch("Solver", desc = "Shows the solution for the ice fill puzzle.").json("Ice fill solver").childOf(::fillDropdown)
    private val fillColour by colourPicker("Colour", Colour.MAGENTA, allowAlpha = true).json("Ice fill colour").childOf(::fillSolver)
    private val fillAuto by switch("Auto", desc = "Automatically completes the ice fill puzzle.").json("Auto ice fill").childOf(::fillDropdown)
    private val fillDelay by slider("Delay", 2, 1, 10, 1, unit = "t").json("Auto ice fill delay").childOf(::fillAuto)
    private val fillReposition by switch("Auto reposition").json("Ice fill auto reposition").childOf(::fillAuto)

    private val mazeDropdown by text("Teleport maze")
    private val mazeSolver by switch("Solver", desc = "Shows the solution for the TP maze puzzle.").json("Maze solver").childOf(::mazeDropdown)
    private val mazeColourOne by colourPicker("Colour for one", Colour.MINECRAFT_GREEN.withAlpha(0.5f), true, desc = "Colour for when there is a single solution.").childOf(::mazeSolver)
    private val mazeColourMultiple by colourPicker("Colour for multiple", Colour.MINECRAFT_GOLD.withAlpha(0.5f), true, desc = "Colour for when there are multiple solutions.").childOf(::mazeSolver)
    private val mazeColourVisited by colourPicker("Colour for visited", Colour.MINECRAFT_RED.withAlpha(0.5f), true, desc = "Colour for the already used TP pads.").childOf(::mazeSolver)
    private val mazeAuto by switch("Auto").json("Auto maze").childOf(::mazeDropdown).asParent()

    private val quizDropdown by text("Quiz")
    private val quizSolver by switch("Solver", desc = "Solver for the trivia puzzle.").json("Quiz solver").childOf(::quizDropdown)
    private val quizColour by colourPicker("Colour", Colour.MINECRAFT_GREEN.withAlpha(0.75f), true, desc = "Color for the quiz solver.").json("Quiz colour").childOf(::quizSolver)
    private val quizDepth by switch("Depth", desc = "Depth check for the trivia puzzle.").json("Quiz depth").childOf(::quizSolver)
    private val quizAuto by switch("Auto").json("Auto quiz").childOf(::quizDropdown).asParent()

    private val weirdosDropdown by text("Three weirdos")
    private val weirdosSolver by switch("Solver", desc = "Shows the solution for the Weirdos puzzle.").json("Weirdos solver").childOf(::weirdosDropdown)
    private val weirdosColour by colourPicker("Correct colour", Colour.MINECRAFT_GREEN.withAlpha(0.7f), true, desc = "Colour for the weirdos solver.").json("Weirdos correct colour").childOf(::weirdosSolver)
    private val weirdosWrongColour by colourPicker("Wrong colour", Colour.MINECRAFT_RED.withAlpha(0.7f), true,  desc = "Colour for the incorrect Weirdos.").json("Weirdos wrong colour").childOf(::weirdosSolver)
    private val weirdosStyle by selector("Style", "Box", arrayListOf("Box", "Filled box"), desc = "Whether or not the box should be filled.").json("Weirdos style").childOf(::weirdosSolver)
    private val weirdosAuto by switch("Auto").json("Auto weirdos").childOf(::weirdosDropdown).asParent()

    private val tttDropdown by text("Tic tac toe")
    private val tttSolver by switch("Solver", desc = "Shows the solution for the Tic tac toe puzzle.").json("Tic tac toe solver").childOf(::tttDropdown)
    private val tttColour by colourPicker("Colour", Colour.MINECRAFT_GREEN.withAlpha(0.7f), true, desc = "Colour for the tic tac toe solver").json("Tic tac toe colour").childOf(::tttSolver)
    private val tttPrediction by switch("Prediction", desc = "try and see").json("Tic tac toe prediction").childOf(::tttSolver)
    private val tttPColour by colourPicker("Prediction colour", Colour.MINECRAFT_YELLOW.withAlpha(0.7f), true).childOf(::tttPrediction)
    private val tttAuto by switch("Auto").json("Auto tic tac toe").childOf(::tttDropdown).asParent()

    private val wbDropdown by text("Water board")
    private val wbSolver by switch("Solver", desc = "Shows the solution to the water board puzzle.").json("Water board solver").childOf(::wbDropdown)
    private val wbTracer by switch("Tracer", true, desc = "Shows a tracer to the next lever.").json("Water board tracer").childOf(::wbSolver)
    private val wbFirst by colourPicker("First", Colour.MINECRAFT_GREEN, true, desc = "Colour for the first tracer.").json("Water board colour first").childOf(::wbTracer)
    private val wbSecond by colourPicker("Second", Colour.MINECRAFT_GOLD, true, desc = "Colour for the second tracer.").json("Water board colour second").childOf(::wbTracer)
    private val wbOptimised by switch("Optimised solutions", desc = "Uses optimised solutions for the water board puzzle.").childOf(::wbDropdown).asParent()
    private val wbAuto by switch("Auto").json("Auto waterboard").childOf(::wbDropdown).asParent()

    private val beamsDropdown by text("Creeper beams")
    private val beamsSolver by switch("Solver", desc = "Shows the solution for the creeper beams puzzle.").json("Creeper beams solver").childOf(::beamsDropdown)
    private val beamsTracer by switch("Tracer").json("Beams tracer").childOf(::beamsSolver)
    private val beamsStyle by selector("Style", "Box", arrayListOf("Box", "Filled box"), desc = "Render style to be used.").json("Beams style").childOf(::beamsSolver)
    private val beamsAlpha by slider("Colour alpha", 0.7f, 0f, 1f, 0.05f).json("Beams colour alpha").childOf(::beamsSolver)
    private val beamsAnnounce by switch("Announce completion", desc = "Sends complete message.").childOf(::beamsDropdown).asParent()
    private val beamsAuto by switch("Auto").json("Auto beams").childOf(::beamsDropdown).asParent()

    private val blazeDropdown by text("Blaze")
    private val blazeSolver by switch("Solver", desc = "Shows the solution for the blaze puzzle.").json("Blaze solver").childOf(::blazeDropdown)
    private val blazeLineNext by switch("Next line", desc = "Shows the next line to click.").json("Blaze solver next line").childOf(::blazeSolver)
    private val blazeLineAmount by slider("Lines amount", 1, 1, 10, 1, desc = "Amount of lines to show.").json("Blaze solver lines amount").childOf(::blazeLineNext)
    private val blazeLineWidth by slider("Lines width", 2f, 0.5f, 5f, 0.1f, desc = "Width for blaze lines.").json("Blaze solver lines width").childOf(::blazeLineNext)
    private val blazeStyle by selector("Style", "Box", arrayListOf("Box", "Filled box"), desc = "Render style to be used.").json("Blaze style").childOf(::blazeSolver)
    private val blazeFirstColour by colourPicker("First colour", Colour.MINECRAFT_GREEN.withAlpha(0.75f), desc = "Colour for the first blaze.").childOf(::blazeSolver)
    private val blazeSecondColour by colourPicker("Second colour", Colour.MINECRAFT_GOLD.withAlpha(0.75f), desc = "Colour for the second blaze.").childOf(::blazeSolver)
    private val blazeAllColour by colourPicker("Other colour", Colour.WHITE.withAlpha(0.3f), desc = "Colour for the other blazes.").childOf(::blazeSolver)
    private val blazeAnnounce by switch("Announce completion", desc = "Sends complete message.").childOf(::blazeDropdown).asParent()
    private val blazeAuto by switch("Auto").json("Auto blaze").childOf(::blazeDropdown)
    private val blazeReposition by switch("Auto reposition").json("Blaze auto reposition").childOf(::blazeAuto)

    private val pathDropdown by text("Ice path")
    private val pathSolver by switch("Solver", desc = "Shows the solution for the ice path puzzle.").json("Path solver").childOf(::pathDropdown)
    private val pathColour by colourPicker("Colour", Colour.MINECRAFT_GREEN, desc = "Colour for the solver.").json("Path colour").childOf(::pathSolver)
    private val pathAuto by switch("Auto").json("Auto path").childOf(::pathDropdown).asParent()

    private val bowDropdown by text("Bow settings").visibleIf { beamsAuto || blazeAuto || pathAuto }
    private val shootCd by slider("Shoot cooldown", 500L, 250L, 1000L, 50L, unit = "ms").childOf(::bowDropdown)
    private val missCd by slider("Miss cooldown", 550L, 300L, 1050L, 50L, unit = "ms").childOf(::bowDropdown)

    private val weirdosRegex = Regex("\\[NPC] (.+): (.+).?")
    private val inPuzzle get() = currentRoom?.data?.type == RoomType.PUZZLE

    init {
        scheduleLoop(10) {
            if (!enabled || !inPuzzle) return@scheduleLoop
            if (blazeSolver || blazeAuto) BlazeSolver.getBlaze()
            if (wbSolver || wbAuto) WaterSolver.scan(wbOptimised)
        }

        on<WorldEvent.Change> {
            IceFillSolver.reset()
            BeamsSolver.reset()
            BlazeSolver.reset()
            MazeSolver.reset()
            IcePathSolver.reset()
            WeirdosSolver.reset()
            QuizSolver.reset()
            TicTacToeSolver.reset()
            WaterSolver.reset()
        }

        on<DungeonEvent.Room.Enter> {
            IceFillSolver.onRoomEnter(room)
            BeamsSolver.onRoomEnter(room)
            BlazeSolver.onRoomEnter(room)
            MazeSolver.onRoomEnter(room)
            IcePathSolver.onRoomEnter(room)
            WeirdosSolver.onRoomEnter(room)
            QuizSolver.onRoomEnter(room)
            TicTacToeSolver.onRoomEnter(room)
        }

        on<RenderEvent.World> {
            if (quizSolver)    QuizSolver.onRenderWorld(ctx, quizColour, quizDepth)
            if (!inPuzzle)     return@on
            if (fillSolver)    IceFillSolver.onRenderWorld(ctx, fillColour)
            if (beamsSolver)   BeamsSolver.onRenderWorld(ctx, beamsStyle.selected, beamsTracer, beamsAlpha)
            if (blazeSolver)   BlazeSolver.onRenderWorld(ctx, blazeLineNext, blazeLineAmount, blazeStyle.selected, blazeFirstColour, blazeSecondColour, blazeAllColour, blazeAnnounce, blazeLineWidth, blazeReposition)
            if (mazeSolver)    MazeSolver.onRenderWorld(ctx, mazeColourOne, mazeColourMultiple, mazeColourVisited)
            if (pathSolver)    IcePathSolver.onRenderWorld(ctx, pathColour)
            if (weirdosSolver) WeirdosSolver.onRenderWorld(ctx, weirdosColour, weirdosWrongColour, weirdosStyle.selected)
            if (tttSolver)     TicTacToeSolver.onRenderWorld(ctx, tttColour, tttPColour, tttPrediction)
            if (wbSolver)      WaterSolver.onRenderWorld(ctx, wbTracer, wbFirst, wbSecond)
        }

        on<TickEvent.End> {
            if (!inPuzzle) return@on
            if (fillAuto)    IceFillSolver.onTick(player, fillDelay, fillReposition)
            if (beamsAuto)   BeamsSolver.onTick(player, shootCd, missCd)
            if (blazeAuto)   BlazeSolver.onTick(player, shootCd, missCd, blazeReposition)
            if (mazeAuto)    MazeSolver.onTick(player)
            if (quizAuto)    QuizSolver.onTick(player)
            if (weirdosAuto) WeirdosSolver.onTick(player)
            if (wbAuto)      WaterSolver.onTick(player)
            if (pathSolver || pathAuto) IcePathSolver.onTick(player, pathAuto, shootCd, missCd)
            if (tttSolver || tttAuto)   TicTacToeSolver.onTick(player, level, tttPrediction, tttAuto)
        }

        on<TickEvent.Server> {
            if (wbSolver) WaterSolver.onServerTick()
        }

        on<ChatEvent.Packet> {
            val msg = message.noControlCodes
            if (quizSolver || quizAuto) QuizSolver.onMessage(msg)
            if (weirdosSolver || weirdosAuto) weirdosRegex.find(msg)?.destructured?.let { (npc, message) -> WeirdosSolver.onMessage(npc, message) }
        }

        on<BlockUpdateEvent> {
            if (!inPuzzle) return@on
            if (beamsSolver || beamsAuto) BeamsSolver.onBlockChange(this@on, beamsAnnounce, beamsAuto)
        }

        on<PacketEvent.Received> {
            if (!inPuzzle) return@on
            when (packet) {
                is ClientboundSoundPacket          -> if (beamsSolver || beamsAuto) BeamsSolver.onSound(packet)
                is ClientboundPlayerPositionPacket -> if (mazeSolver || mazeAuto)   MazeSolver.onPosition(packet)
            }
        }

        on<PacketEvent.Sent, ServerboundUseItemOnPacket> {
            if (!inPuzzle) return@on
            if (wbSolver || wbAuto) WaterSolver.onInteract(packet)
        }
    }
}