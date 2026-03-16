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
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.*
import quoi.utils.Scheduler.scheduleLoop
import quoi.utils.StringUtils.noControlCodes

object PuzzleSolvers : Module(
    "Puzzle Solvers",
    area = Island.Dungeon(inClear = true)
) {
    private val fillDropdown by DropdownSetting("Ice fill").collapsible()
    private val fillSolver by BooleanSetting("Solver", desc = "Shows the solution for the ice fill puzzle.").json("Ice fill solver").withDependency(fillDropdown)
    private val fillColour by ColourSetting("Colour", Colour.MAGENTA, allowAlpha = true).json("Ice fill colour").withDependency(fillDropdown) { fillSolver }
    private val fillAuto by BooleanSetting("Auto", desc = "Automatically completes the ice fill puzzle.").json("Auto ice fill").withDependency(fillDropdown)
    private val fillDelay by NumberSetting("Delay", 2, 1, 10, 1, unit = "t").json("Auto ice fill delay").withDependency(fillDropdown) { fillAuto }
    private val fillReposition by BooleanSetting("Auto reposition").json("Ice fill auto reposition").withDependency(fillDropdown) { fillAuto }

    private val mazeDropdown by DropdownSetting("Teleport maze").collapsible()
    private val mazeSolver by BooleanSetting("Solver", desc = "Shows the solution for the TP maze puzzle.").json("Maze solver").withDependency(mazeDropdown)
    private val mazeColourOne by ColourSetting("Colour for one", Colour.MINECRAFT_GREEN.withAlpha(0.5f), true, desc = "Colour for when there is a single solution.").withDependency(mazeDropdown) { mazeSolver }
    private val mazeColourMultiple by ColourSetting("Colour for multiple", Colour.MINECRAFT_GOLD.withAlpha(0.5f), true, desc = "Colour for when there are multiple solutions.").withDependency(mazeDropdown) { mazeSolver }
    private val mazeColourVisited by ColourSetting("Colour for visited", Colour.MINECRAFT_RED.withAlpha(0.5f), true, desc = "Colour for the already used TP pads.").withDependency(mazeDropdown) { mazeSolver }
    private val mazeAuto by BooleanSetting("Auto").json("Auto maze").withDependency(mazeDropdown)

    private val quizDropdown by DropdownSetting("Quiz").collapsible()
    private val quizSolver by BooleanSetting("Solver", desc = "Solver for the trivia puzzle.").json("Quiz solver").withDependency(quizDropdown)
    private val quizColour by ColourSetting("Colour", Colour.MINECRAFT_GREEN.withAlpha(0.75f), true, desc = "Color for the quiz solver.").json("Quiz colour").withDependency(quizDropdown) { quizSolver }
    private val quizDepth by BooleanSetting("Depth", desc = "Depth check for the trivia puzzle.").json("Quiz depth").withDependency(quizDropdown) { quizSolver }
    private val quizAuto by BooleanSetting("Auto").json("Auto quiz").withDependency(quizDropdown)

    private val weirdosDropdown by DropdownSetting("Three weirdos").collapsible()
    private val weirdosSolver by BooleanSetting("Solver", desc = "Shows the solution for the Weirdos puzzle.").json("Weirdos solver").withDependency(weirdosDropdown)
    private val weirdosColour by ColourSetting("Correct colour", Colour.MINECRAFT_GREEN.withAlpha(0.7f), true, desc = "Colour for the weirdos solver.").json("Weirdos correct colour").withDependency(weirdosDropdown) { weirdosSolver }
    private val weirdosWrongColour by ColourSetting("Wrong colour", Colour.MINECRAFT_RED.withAlpha(0.7f), true,  desc = "Colour for the incorrect Weirdos.").json("Weirdos wrong colour").withDependency(weirdosDropdown) { weirdosSolver }
    private val weirdosStyle by SelectorSetting("Style", "Box", arrayListOf("Box", "Filled box"), desc = "Whether or not the box should be filled.").json("Weirdos style").withDependency(weirdosDropdown) { weirdosSolver }
    private val weirdosAuto by BooleanSetting("Auto").json("Auto weirdos").withDependency(weirdosDropdown)

    private val tttDropdown by DropdownSetting("Tic tac toe").collapsible()
    private val tttSolver by BooleanSetting("Solver", desc = "Shows the solution for the Tic tac toe puzzle.").json("Tic tac toe solver").withDependency(tttDropdown)
    private val tttColour by ColourSetting("Colour", Colour.MINECRAFT_GREEN.withAlpha(0.7f), true, desc = "Colour for the tic tac toe solver").json("Tic tac toe colour").withDependency(tttDropdown) { tttSolver }
    private val tttPrediction by BooleanSetting("Prediction", desc = "try and see").json("Tic tac toe prediction").withDependency(tttDropdown) { tttSolver }
    private val tttPColour by ColourSetting("Prediction colour", Colour.MINECRAFT_YELLOW.withAlpha(0.7f), true).withDependency(tttDropdown) { tttSolver && tttPrediction }
    private val tttAuto by BooleanSetting("Auto").json("Auto tic tac toe").withDependency(tttDropdown)

    private val wbDropdown by DropdownSetting("Water board").collapsible()
    private val wbSolver by BooleanSetting("Solver", desc = "Shows the solution to the water board puzzle.").json("Water board solver").withDependency(wbDropdown)
    private val wbTracer by BooleanSetting("Tracer", true, desc = "Shows a tracer to the next lever.").json("Water board tracer").withDependency(wbDropdown) { wbSolver }
    private val wbFirst by ColourSetting("First", Colour.MINECRAFT_GREEN, true, desc = "Colour for the first tracer.").json("Water board colour first").withDependency(wbDropdown) { wbTracer && wbSolver }
    private val wbSecond by ColourSetting("Second", Colour.MINECRAFT_GOLD, true, desc = "Colour for the second tracer.").json("Water board colour second").withDependency(wbDropdown) { wbTracer && wbSolver }
    private val wbOptimised by BooleanSetting("Optimised solutions", desc = "Uses optimised solutions for the water board puzzle.").withDependency(wbDropdown)
    private val wbAuto by BooleanSetting("Auto").json("Auto waterboard").withDependency(wbDropdown)

    private val beamsDropdown by DropdownSetting("Creeper beams").collapsible()
    private val beamsSolver by BooleanSetting("Solver", desc = "Shows the solution for the creeper beams puzzle.").json("Creeper beams solver").withDependency(beamsDropdown)
    private val beamsTracer by BooleanSetting("Tracer").json("Beams tracer").withDependency(beamsDropdown) { beamsSolver }
    private val beamsStyle by SelectorSetting("Style", "Box", arrayListOf("Box", "Filled box"), desc = "Render style to be used.").json("Beams style").withDependency(beamsDropdown) { beamsSolver }
    private val beamsAlpha by NumberSetting("Colour alpha", 0.7f, 0f, 1f, 0.05f).json("Beams colour alpha").withDependency(beamsDropdown) { beamsSolver }
    private val beamsAnnounce by BooleanSetting("Announce completion", desc = "Sends complete message.").withDependency(beamsDropdown)
    private val beamsAuto by BooleanSetting("Auto").json("Auto beams").withDependency(beamsDropdown)

    private val blazeDropdown by DropdownSetting("Blaze").collapsible()
    private val blazeSolver by BooleanSetting("Solver", desc = "Shows the solution for the blaze puzzle.").json("Blaze solver").withDependency(blazeDropdown)
    private val blazeLineNext by BooleanSetting("Next line", desc = "Shows the next line to click.").json("Blaze solver next line").withDependency(blazeDropdown) { blazeSolver }
    private val blazeLineAmount by NumberSetting("Lines amount", 1, 1, 10, 1, desc = "Amount of lines to show.").json("Blaze solver lines amount").withDependency(blazeDropdown) { blazeSolver && blazeLineNext }
    private val blazeLineWidth by NumberSetting("Lines width", 2f, 0.5f, 5f, 0.1f, desc = "Width for blaze lines.").json("Blaze solver lines width").withDependency(blazeDropdown) { blazeSolver && blazeLineNext }
    private val blazeStyle by SelectorSetting("Style", "Box", arrayListOf("Box", "Filled box"), desc = "Render style to be used.").json("Blaze style").withDependency(blazeDropdown) { blazeSolver }
    private val blazeFirstColour by ColourSetting("First colour", Colour.MINECRAFT_GREEN.withAlpha(0.75f), desc = "Colour for the first blaze.").withDependency(blazeDropdown) { blazeSolver }
    private val blazeSecondColour by ColourSetting("Second colour", Colour.MINECRAFT_GOLD.withAlpha(0.75f), desc = "Colour for the second blaze.").withDependency(blazeDropdown) { blazeSolver }
    private val blazeAllColour by ColourSetting("Other colour", Colour.WHITE.withAlpha(0.3f), desc = "Colour for the other blazes.").withDependency(blazeDropdown) { blazeSolver }
    private val blazeAnnounce by BooleanSetting("Announce completion", desc = "Sends complete message.").withDependency(blazeDropdown)
    private val blazeAuto by BooleanSetting("Auto").json("Auto blaze").withDependency(blazeDropdown)
    private val blazeReposition by BooleanSetting("Auto reposition").json("Blaze auto reposition").withDependency(blazeDropdown) { blazeAuto }

    private val pathDropdown by DropdownSetting("Ice path").collapsible()
    private val pathSolver by BooleanSetting("Solver", desc = "Shows the solution for the ice path puzzle.").json("Path solver").withDependency(pathDropdown)
    private val pathColour by ColourSetting("Colour", Colour.MINECRAFT_GREEN, desc = "Colour for the solver.").json("Path colour").withDependency(pathDropdown) { pathSolver }
    private val pathAuto by BooleanSetting("Auto").json("Auto path").withDependency(pathDropdown)

    private val bowDropdown by DropdownSetting("Bow settings").collapsible().withDependency { beamsAuto || blazeAuto || pathAuto }
    private val shootCd by NumberSetting("Shoot cooldown", 500L, 250L, 1000L, 50L, unit = "ms").withDependency(bowDropdown)
    private val missCd by NumberSetting("Miss cooldown", 550L, 300L, 1050L, 50L, unit = "ms").withDependency(bowDropdown)

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
            if (pathSolver || pathAuto) IcePathSolver.onTick(player, level, pathAuto, shootCd, missCd)
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