package quoi.utils.ui

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.events.core.AreaBoundListener
import quoi.module.settings.SettingsDSL
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.EntityUtils.distanceToCamera
import quoi.utils.EntityUtils.renderPos
import quoi.utils.render.drawLine
import quoi.utils.render.drawTracer

/**
 * A [ToggleableGroup] for rendering tracer lines
 * Represents a switch with optional colour and distance settings
 *
 * @param parent Parent
 * @param name Switch name
 * @param colour Default outline colour. If `null`, the colour picker is hidden
 * @param customColour Whether to show a `Custom colour` toggle for the colour
 * @param distance Default max render distance. If `null`, the distance slider and distance check are hidden and disabled.
 *
 * @see [SettingsDSL.tracer]
 */
class TracerSettings(
    parent: AreaBoundListener,
    name: String = "Tracer",
    colour: Colour? = Colour.WHITE,
    customColour: Boolean = false,
    distance: Int? = 256,
) : ToggleableGroup(parent, name) {

    private val customCol = switch("Custom colour").also {
        if (customColour && colour != null) +it
        else it.value = true
    }

    private val colour = colour?.let { col ->
        colourPicker("Colour", col, allowAlpha = true).also {
            if (!customColour) +it
            else +it.childOf(customCol)
        }
    }

    private val distance = distance?.let {
        +slider("Max distance", it, 0, 512, 1)
    }
    private val thickness by slider("Thickness", 4f, 0.5f, 8f, 0.5f)

    /**
     * Draws a tracer line
     * @param colour colour used if the custom colour toggle is off
     * @param overrideColour if not `null` forces this colour ignoring custom colour switch and picker
     */
    fun draw(
        ctx: LevelRenderContext,
        to: Vec3,
        colour: Colour = Colour.WHITE,
        overrideColour: Colour? = null,
        distanceToCamera: Double = 6767.0
    ) {
        if (!enabled) return

        if (distance != null && distanceToCamera > distance.value) return

        val c = overrideColour ?: if (this.colour != null && customCol.value) this.colour.value else colour
        ctx.drawTracer(to, c, thickness, depth = false)
    }

    /**
     * Draws a tracer line to entity
     */
    fun draw(
        ctx: LevelRenderContext,
        to: Entity,
        colour: Colour = Colour.WHITE,
        overrideColour: Colour? = null
    ) = draw(ctx, to.renderPos.add(0.0, 1.5, 0.0), colour, overrideColour, to.distanceToCamera)

    /**
     * Draws line between points
     */
    fun draw(
        ctx: LevelRenderContext,
        points: Collection<Vec3>,
        colour: Colour = Colour.WHITE,
        overrideColour: Colour? = null
    ) {
        if (!enabled) return

        val c = overrideColour ?: if (this.colour != null && customCol.value) this.colour.value else colour
        ctx.drawLine(points, c, false, thickness)
    }
}