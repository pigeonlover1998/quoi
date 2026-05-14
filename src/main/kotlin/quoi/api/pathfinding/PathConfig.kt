package quoi.api.pathfinding

import quoi.module.impl.render.ClickGui

data class PathConfig(
    val yawStep: Float = ClickGui.yawStep,
    val pitchStep: Float = ClickGui.pitchStep,
    val hWeight: Double = ClickGui.hWeight,
    val threads: Int = ClickGui.threads,
    val timeout: Long = ClickGui.timeout
)