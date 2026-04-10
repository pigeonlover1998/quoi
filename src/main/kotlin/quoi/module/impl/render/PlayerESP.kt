package quoi.module.impl.render

import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.EntityEvent
import quoi.api.events.RenderEvent
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.EntityUtils.colourFromDistance
import quoi.utils.EntityUtils.distanceToCamera
import quoi.utils.EntityUtils.interpolatedBox
import quoi.utils.EntityUtils.playerEntitiesNoSelf
import quoi.utils.EntityUtils.renderPos
import quoi.utils.equalsOneOf
import quoi.utils.render.drawStyledBox
import quoi.utils.render.drawTracer

object PlayerESP : Module(
    "Player ESP",
    desc = "Highlights players through walls."
) {
    private val tracer by switch("Tracer").json("Tracer toggle")
    private val tracerCustomCol by switch("Custom colour").json("Tracer distance colours").childOf(::tracer)
    private val tracerColour by colourPicker("Colour", Colour.WHITE).json("Tracer colour").childOf(::tracerCustomCol)
    private val tracerDistance by slider("Max distance", 256, 0, 256, 1).childOf(::tracer)
    private val tracerThickness by slider("Thickness", 4f, 1f, 8f, 1f).json("Tracer thickness").childOf(::tracer)

    private val ironmenOnly by switch("Ir*nmen only")
    private val depth by switch("Depth check")
    private val style by selector("Style", "Box", arrayListOf("Box", "Filled box", "Glow", "2D"), desc = "Esp render style to be used.")
    private val customCol by switch("Custom colour").json("Box custom colour").visibleIf { !style.selected.equalsOneOf("Glow", "2D") }
    private val colour by colourPicker("Colour", Colour.WHITE, desc = "Colour for the Player ESP").childOf(::customCol)
    private val fillCustomCol by switch("Fill custom colour", true).visibleIf { style.selected == "Filled box" }
    private val fillColour by colourPicker("Colour", Colour.WHITE.withAlpha(60), allowAlpha = true, desc = "Fill colour for the Player ESP").json("Fill colour").childOf(::fillCustomCol).visibleIf { style.selected == "Filled box" }
    private val thickness by slider("Thickness", 4f, 1f, 8f, 1f)
    private val sizeOffset by slider("Size offset", 0.0, -1.0, 1.0, 0.05, desc = "Changes box size offset.").visibleIf { style.selected.equalsOneOf("Box", "Filled box") }

    init {
        on<RenderEvent.World> {
            playerEntitiesNoSelf.forEach { entity ->
                if (ironmenOnly && entity.displayName?.string?.contains("♲") == false) return@forEach
                val aabb = entity.interpolatedBox.inflate(sizeOffset, 0.0, sizeOffset)
                val c = if (customCol) colour else entity.colourFromDistance
                val fc = if (fillCustomCol) fillColour else entity.colourFromDistance.withAlpha(fillColour.alpha)

                ctx.drawStyledBox(style.selected, aabb, c, fc, thickness, depth)

                if (tracer) {
                    if (entity.distanceToCamera > tracerDistance) return@forEach
                    val col = if (tracerCustomCol) tracerColour else entity.colourFromDistance
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