package quoi.utils.ui

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.world.phys.AABB
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.EntityEvent
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.module.settings.group.SettingGroup
import quoi.module.settings.impl.SelectorComponent
import quoi.utils.equalsOneOf
import quoi.utils.render.drawStyledBox
import quoi.module.settings.SettingsDSL

/**
 * A [SettingGroup] for rendering esp boxes
 * Represents a selector with styles (Box, Filled box, Glow) with optional colour and other settings
 *
 * @param module Parent module
 * @param name Selector name
 * @param desc Selector description
 * @param colour Default outline colour. If `null`, the outline colour picker is hidden
 * @param fillColour Fill colour. If `null`, the fill colour picker is hidden
 * @param glow Whether to include `Glow` in available render styles
 * @param customColour Whether to show a `Custom colour` toggle for the outline
 * @param customFillColour Whether to show a `Custom colour` toggle for the outline
 * @param aabbOffset Whether to show a slider to inflate/deflate the aabb size
 *
 * @see [SettingsDSL.highlight]
 */
class HighlightSettings( // kinda ugly but it works
    module: Module,
    name: String = "Style",
    desc: String = "Esp render style to be used.",
    colour: Colour? = Colour.WHITE,
    fillColour: Colour? = Colour.WHITE.withAlpha(67),
    glow: Boolean = true,
    customColour: Boolean = false,
    customFillColour: Boolean = false,
    private val aabbOffset: Boolean = false,
): SettingGroup(
    module,
    SelectorComponent(
        name,
        "Box",
        buildList {
            add("Box")
            add("Filled box")
            if (glow) add("Glow")
        },
        desc = desc
    )
) {
    @Suppress("unchecked_cast")
    val style: String
        get() = (component as SelectorComponent<String>).selected

    private val customCol = switch("Custom colour").also {
        if (customColour && colour != null) +it
        else it.value = true
    }
    private val _outline = colour?.let { col ->
        colourPicker("Colour", col, allowAlpha = true).also {
            if (!customColour) +it
            else it.childOf(customCol)
        }
    }
    val outline: Colour
        get() = _outline?.value ?: Colour.WHITE

    private val fillCustomCol = switch("Fill custom colour")
        .visibleIf { style == "Filled box" }
        .also {
            if (customFillColour && fillColour != null) +it
            else it.value = true
        }

    private val _fill = fillColour?.let { col ->
        colourPicker("Fill colour", col, allowAlpha = true)
            .visibleIf { style == "Filled box" }
            .also {
                if (!customFillColour) +it
                else +it.childOf(fillCustomCol)
            }
    }
    val fill: Colour
        get() = _fill?.value ?: Colour.WHITE

    private val depth by switch("Depth check")
    private val thickness by slider("Thickness", 4f, 1f, 8f, 0.5f)
        .visibleIf { style.equalsOneOf("Box", "Filled box") }
    private val sizeOffset = slider("Size offset", 0.0, -1.0, 1.0, 0.05, desc = "Changes box size offset.")
        .visibleIf { style.equalsOneOf("Box", "Filled box") }
        .also { if (aabbOffset) +it }

    /**
     * Draws a styled bounding box
     * @param colour outline colour used if the custom colour toggle is off
     * @param overrideColour if not `null` forces this colour ignoring custom colour switch and picker
     */
    fun draw(
        ctx: LevelRenderContext,
        aabb: AABB,
        colour: Colour = Colour.WHITE,
        fillColour: Colour = Colour.WHITE.withAlpha(67),
        overrideColour: Colour? = null,
        overrideFillColour: Colour? = null,
    ) {
        if (style == "Glow") return

        val box = if (aabbOffset) aabb.inflate(sizeOffset.value, 0.0, sizeOffset.value) else aabb

        val c = overrideColour ?: if (_outline != null && customCol.value) _outline.value else colour
        val fc = overrideFillColour ?: if (_fill != null && fillCustomCol.value) _fill.value else fillColour.withAlpha(_fill?.value?.alpha ?: 1f) // idk maybe make fill colour alpha slider when custom is true but disabled

        ctx.drawStyledBox(style, box, c, fc, thickness, depth)
    }

    /**
     * shit for glow
     */
    fun draw(event: EntityEvent.ForceGlow, colour: Colour = Colour.WHITE, overrideColour: Colour? = null) {
        if (style != "Glow") return
        if (depth && !player.hasLineOfSight(event.entity)) return // todo replace with something better.
        val c = overrideColour ?: if (_outline != null && customCol.value) _outline.value else colour
        event.glowColour = c
    }
}