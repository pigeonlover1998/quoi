package quoi.module.impl.misc.riftsolvers

import quoi.module.Module
import quoi.module.impl.misc.riftsolvers.impl.CraftRoom
import quoi.module.impl.misc.riftsolvers.impl.LavaMaze
import quoi.module.impl.misc.riftsolvers.impl.LavaParkour
import quoi.module.impl.misc.riftsolvers.impl.RedGreen
import quoi.module.impl.misc.riftsolvers.impl.TinyDancer
import quoi.module.impl.misc.riftsolvers.impl.Tubulator

object MirrorverseSolvers : Module(
    "Mirrorverse Solvers",
    desc = "Automatically completes Mirrorverse puzzles: Lava Maze, Lava Parkour, Craft Room, Red Green, Tiny Dancer, and Tubulator.",
    subarea = "Mirrorverse"
) {
    @Suppress("unused")
    private val solvers = setOf(
        LavaMaze,
        LavaParkour,
        CraftRoom,
        RedGreen,
        TinyDancer,
        Tubulator
    )
}