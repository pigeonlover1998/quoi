package quoi.module.impl.misc.riftsolvers

import quoi.module.Module
import quoi.module.impl.misc.riftsolvers.impl.*

@Suppress("unused_expression")
object MirrorverseSolvers : Module(
    "Mirrorverse Solvers",
    desc = "Automatically completes Mirrorverse puzzles: Lava Maze, Lava Parkour, Craft Room, Red Green, Tiny Dancer, and Tubulator.",
    subarea = "Mirrorverse"
) {
    init {
        LavaMaze
        LavaParkour
        CraftRoom
        RedGreen
        TinyDancer
        Tubulator
    }
}