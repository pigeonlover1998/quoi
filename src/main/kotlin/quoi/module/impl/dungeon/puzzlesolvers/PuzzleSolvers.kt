package quoi.module.impl.dungeon.puzzlesolvers

import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
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

object PuzzleSolvers : Module(
    "Puzzle Solvers",
    area = Island.Dungeon(inClear = true)
) {
    private val fillDropdown by DropdownSetting("Ice fill").collapsible()
    private val fillSolver by BooleanSetting("Toggle", desc = "Shows the solution for the ice fill puzzle.").json("Ice fill solver toggle").withDependency(fillDropdown)
    private val fillColour by ColourSetting("Colour", Colour.MAGENTA, allowAlpha = true).json("Ice fill colour").withDependency(fillDropdown) { fillSolver }
    private val fillAuto by BooleanSetting("Auto", desc = "Automatically completes the ice fill puzzle.").json("Auto ice fill").withDependency(fillDropdown) { fillSolver }
    private val fillDelay by NumberSetting("Delay", 2, 1, 10, 1, unit = "t").json("Auto ice fill delay").withDependency(fillDropdown) { fillSolver && fillAuto }

    private val mazeDropdown by DropdownSetting("Teleport maze").collapsible()
    private val mazeSolver by BooleanSetting("Toggle", desc = "Shows the solution for the TP maze puzzle.").json("Maze solver toggle").withDependency(mazeDropdown)
    private val mazeColourOne by ColourSetting("Colour for one", Colour.MINECRAFT_GREEN.withAlpha(0.5f), true, desc = "Colour for when there is a single solution.").withDependency(mazeDropdown) { mazeSolver }
    private val mazeColourMultiple by ColourSetting("Colour for multiple", Colour.MINECRAFT_GOLD.withAlpha(0.5f), true, desc = "Colour for when there are multiple solutions.").withDependency(mazeDropdown) { mazeSolver }
    private val mazeColourVisited by ColourSetting("Colour for visited", Colour.MINECRAFT_RED.withAlpha(0.5f), true, desc = "Colour for the already used TP pads.").withDependency(mazeDropdown) { mazeSolver }
    private val mazeAuto by BooleanSetting("Auto").json("Auto maze").withDependency(mazeDropdown) { mazeSolver }

    private val beamsDropdown by DropdownSetting("Creeper beams").collapsible()
    private val beamsSolver by BooleanSetting("Toggle", desc = "Shows the solution for the creeper beams puzzle.").json("Creeper beams solver toggle").withDependency(beamsDropdown)
    private val beamsAnnounce by BooleanSetting("Announce completion").withDependency(beamsDropdown) { beamsSolver }
    private val beamsTracer by BooleanSetting("Tracer").json("Beams tracer").withDependency(beamsDropdown) { beamsSolver }
    private val beamsStyle by SelectorSetting("Style", "Box", arrayListOf("Box", "Filled box"), desc = "Render style to be used.").json("Beams style").withDependency(beamsDropdown) { beamsSolver }
    private val beamsAlpha by NumberSetting("Colour alpha", 0.7f, 0f, 1f, 0.05f).json("Beams colour alpha").withDependency(beamsDropdown) { beamsSolver }
    private val beamsAuto by BooleanSetting("Auto").json("Auto beams").withDependency(beamsDropdown) { beamsSolver }

    private val blazeDropdown by DropdownSetting("Blaze").collapsible()
    private val blazeSolver by BooleanSetting("Toggle", desc = "Shows the solution for the blaze puzzle.").json("Blaze solver").withDependency(blazeDropdown)
    private val blazeAnnounce by BooleanSetting("Announce completion", desc = "Send complete message.").withDependency(blazeDropdown) { blazeSolver }
    private val blazeLineNext by BooleanSetting("Next line", desc = "Shows the next line to click.").json("Blaze solver next line").withDependency(blazeDropdown) { blazeSolver }
    private val blazeLineAmount by NumberSetting("Lines amount", 1, 1, 10, 1, desc = "Amount of lines to show.").json("Blaze solver lines amount").withDependency(blazeDropdown) { blazeSolver && blazeLineNext }
    private val blazeLineWidth by NumberSetting("Lines width", 2f, 0.5f, 5f, 0.1f, desc = "Width for blaze lines.").json("Blaze solver lines width").withDependency(blazeDropdown) { blazeSolver && blazeLineNext }
    private val blazeStyle by SelectorSetting("Style", "Box", arrayListOf("Box", "Filled box"), desc = "Render style to be used.").json("Blaze style").withDependency(blazeDropdown) { blazeSolver }
    private val blazeFirstColour by ColourSetting("First colour", Colour.MINECRAFT_GREEN.withAlpha(.75f), desc = "Colour for the first blaze.").withDependency(blazeDropdown) { blazeSolver }
    private val blazeSecondColour by ColourSetting("Second colour", Colour.MINECRAFT_GOLD.withAlpha(.75f), desc = "Colour for the second blaze.").withDependency(blazeDropdown) { blazeSolver }
    private val blazeAllColour by ColourSetting("Other colour", Colour.WHITE.withAlpha(.3f), desc = "Colour for the other blazes.").withDependency(blazeDropdown) { blazeSolver }
    private val blazeAuto by BooleanSetting("Auto").json("Auto blaze").withDependency(blazeDropdown) { blazeSolver }
    private val blazeReposition by BooleanSetting("Auto reposition").json("Blaze auto reposition").withDependency(blazeDropdown) { blazeSolver && blazeAuto }

    private val bowDropdown by DropdownSetting("Bow settings").collapsible().withDependency { beamsSolver && beamsAuto || blazeSolver && blazeAuto}
    private val shootCd by NumberSetting("Shoot cooldown", 500L, 250L, 1000L, 50L, unit = "ms").withDependency(bowDropdown)
    private val missCd by NumberSetting("Miss cooldown", 550L, 300L, 1050L, 50L, unit = "ms").withDependency(bowDropdown)

    init {
        scheduleLoop(10) {
            if (!enabled || currentRoom?.data?.type != RoomType.PUZZLE) return@scheduleLoop
            if (blazeSolver) BlazeSolver.getBlaze()
        }

        on<WorldEvent.Change> {
            IceFillSolver.reset()
            BeamsSolver.reset()
            BlazeSolver.reset()
            MazeSolver.reset()
        }

        on<DungeonEvent.Room.Enter> {
            IceFillSolver.onRoomEnter(room)
            BeamsSolver.onRoomEnter(room)
            BlazeSolver.onRoomEnter(room)
            MazeSolver.onRoomEnter(room)
        }

        on<RenderEvent.World> {
            if (fillSolver)  IceFillSolver.onRenderWorld(ctx, fillColour)
            if (beamsSolver) BeamsSolver.onRenderWorld(ctx, beamsStyle.selected, beamsTracer, beamsAlpha)
            if (blazeSolver) BlazeSolver.onRenderWorld(ctx, blazeLineNext, blazeLineAmount, blazeStyle.selected, blazeFirstColour, blazeSecondColour, blazeAllColour, blazeAnnounce, blazeLineWidth, blazeReposition)
            if (mazeSolver)  MazeSolver.onRenderWorld(ctx, mazeColourOne, mazeColourMultiple, mazeColourVisited)
        }

        on<TickEvent.End> {
            if (currentRoom?.data?.type != RoomType.PUZZLE) return@on
            if (fillSolver && fillAuto)   IceFillSolver.onTick(player, fillDelay)
            if (beamsSolver && beamsAuto) BeamsSolver.onTick(player, shootCd, missCd)
            if (blazeSolver && blazeAuto) BlazeSolver.onTick(player, shootCd, missCd, blazeReposition)
            if (mazeSolver && mazeAuto)   MazeSolver.onTick(player)
        }

        on<BlockUpdateEvent> {
            if (beamsSolver) BeamsSolver.onBlockChange(this@on, beamsAnnounce)
        }

        on<PacketEvent.Received> {
            if (currentRoom?.data?.type != RoomType.PUZZLE) return@on
            when (packet) {
                is ClientboundSoundPacket ->          if (beamsSolver && beamsAuto) BeamsSolver.onSound(packet)
                is ClientboundPlayerPositionPacket -> if (mazeSolver) MazeSolver.onPosition(packet)
            }
        }
    }
}