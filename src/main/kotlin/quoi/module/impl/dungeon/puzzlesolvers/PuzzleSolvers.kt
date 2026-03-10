package quoi.module.impl.dungeon.puzzlesolvers

import quoi.api.colour.Colour
import quoi.api.events.DungeonEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.invoke
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.ColourSetting
import quoi.module.settings.impl.DropdownSetting
import quoi.module.settings.impl.NumberSetting

object PuzzleSolvers : Module(
    "Puzzle Solvers",
    area = Island.Dungeon(inClear = true)
) {
    private val fillDropdown by DropdownSetting("Ice fill").collapsible()
    private val fillSolver by BooleanSetting("Ice fill solver", desc = "Shows you the solution to the ice fill puzzle.").withDependency(fillDropdown)
    private val fillColour by ColourSetting("Colour", Colour.MAGENTA, allowAlpha = true).json("Ice fill colour").withDependency(fillDropdown) { fillSolver }
    private val fillAuto by BooleanSetting("Auto", desc = "Automatically completes the ice fill puzzle.").json("Auto ice fill").withDependency(fillDropdown) { fillSolver }
    private val fillDelay by NumberSetting("Delay", 2, 1, 10, 1, unit = "t").json("Auto ice fill delay").withDependency(fillDropdown) { fillSolver && fillAuto }

    init {
        on<WorldEvent.Change> {
            IceFillSolver.reset()
        }

        on<DungeonEvent.Room.Enter> {
            IceFillSolver.onRoomEnter(room)
        }

        on<RenderEvent.World> {
            if (fillSolver) IceFillSolver.onRenderWorld(ctx, fillColour)
        }

        on<TickEvent.End> {
            if (fillSolver && fillAuto) IceFillSolver.onTick(player, fillDelay)
        }
    }
}