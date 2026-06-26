package quoi.api.pathfinding

import quoi.module.impl.render.clickgui.impl.PathSettings

data class PathConfig(
    val yawStep: Float = PathSettings.yawStep,
    val pitchStep: Float = PathSettings.pitchStep,
    val hWeight: Double = PathSettings.hWeight,
    val threads: Int = PathSettings.threads,
    val timeout: Long = PathSettings.timeout,
    val feedback: Boolean = true
)