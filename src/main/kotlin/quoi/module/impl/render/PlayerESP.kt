package quoi.module.impl.render

import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.EntityEvent
import quoi.api.events.RenderEvent
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.*
import quoi.utils.equalsOneOf
import quoi.utils.render.drawStyledBox
import quoi.utils.render.drawTracer
import quoi.utils.EntityUtils.colourFromDistance
import quoi.utils.EntityUtils.distanceToCamera
import quoi.utils.EntityUtils.interpolatedBox
import quoi.utils.EntityUtils.playerEntitiesNoSelf
import quoi.utils.EntityUtils.renderPos

object PlayerESP : Module(
    "Player ESP",
    desc = "Highlights players through walls."
) {
    private val tracerDropdown by DropdownSetting("Tracer").collapsible()
    private val tracer by BooleanSetting("Toggle").json("Tracer toggle").withDependency(tracerDropdown)
    private val tracerDistCols by BooleanSetting("Distance colours").json("Tracer distance colours").withDependency(tracerDropdown) { tracer }
    private val tracerColour by ColourSetting("Colour", Colour.WHITE).json("Tracer colour").withDependency(tracerDropdown) { tracer && !tracerDistCols }
    private val tracerDistance by NumberSetting("Max distance", 256, 0, 256, 1).withDependency(tracerDropdown) { tracer }
    private val tracerThickness by NumberSetting("Thickness", 4f, 1f, 8f, 1f).json("Tracer thickness").withDependency(tracerDropdown) { tracer }

    private val ironmenOnly by BooleanSetting("Ir*nmen only")
    private val depth by BooleanSetting("Depth check")
    private val style by SelectorSetting("Style", "Box", arrayListOf("Box", "Filled box", "Glow", "2D"), desc = "Esp render style to be used.")
    private val distCols by BooleanSetting("Distance colours").withDependency { !style.selected.equalsOneOf("Glow", "2D") }
    private val colour by ColourSetting("Colour", Colour.WHITE, desc = "Colour for the Player ESP").withDependency { !distCols }
    private val fillDistCols by BooleanSetting("Fill distance colours", true).withDependency { style.selected == "Filled box" }
    private val fillColour by ColourSetting("Fill colour", Colour.WHITE.withAlpha(60), allowAlpha = true, desc = "Fill colour for the Player ESP").withDependency { style.selected == "Filled box" && !fillDistCols }
    private val thickness by NumberSetting("Thickness", 4f, 1f, 8f, 1f)
    private val sizeOffset by NumberSetting("Size offset", 0.0, -1.0, 1.0, 0.05, desc = "Changes box size offset.").withDependency { style.selected.equalsOneOf("Box", "Filled box") }

    init {
        on<RenderEvent.World> {
            playerEntitiesNoSelf.forEach { entity ->
                if (ironmenOnly && entity.displayName?.string?.contains("♲") == false) return@forEach
                val aabb = entity.interpolatedBox.inflate(sizeOffset, 0.0, sizeOffset)
                val c = if (distCols) entity.colourFromDistance else colour
                val fc = if (fillDistCols) entity.colourFromDistance.withAlpha(fillColour.alpha) else fillColour

                ctx.drawStyledBox(style.selected, aabb, c, fc, thickness, depth)

                if (tracer) {
                    if (entity.distanceToCamera > tracerDistance) return@forEach
                    val col = if (tracerDistCols) entity.colourFromDistance else tracerColour
                    ctx.drawTracer(entity.renderPos.add(0.0, 1.5, 0.0), col, tracerThickness, depth)
                }
            }
        }

        on<EntityEvent.ForceGlow> {
            if (style.selected != "Glow") return@on
            if (ironmenOnly && entity.displayName?.string?.contains("♲") == false) return@on
            if (entity !in playerEntitiesNoSelf) return@on
            glowColour = colour
        }
    }
}