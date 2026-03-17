package quoi.module.impl.dungeon

import net.minecraft.world.phys.AABB
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.ChatEvent
import quoi.api.events.RenderEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.invoke
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawWireFrameBox

// Kyleen
object NecronPlatformHighlight : Module(
    "Necron Platform Highlight",
    desc = "Highlights 3x3 area to mine after Goldor dies.",
    area = Island.Dungeon(7, inBoss = true)
) {

    private val colour by colourPicker("Colour", Colour.GREEN.withAlpha(60), true)
    private val wireframe by switch("Show outline")
    private val lineColour by colourPicker("Outline colour", Colour.GREEN.withAlpha(255), true).visibleIf { wireframe }
    private val lineWidth by slider("Outline width", 2.0, 0.1, 10.0, 0.1).visibleIf { wireframe }
    private val depth by switch("Depth check")

    private var shouldHighlightBlocks = false
    private val healerBox = AABB(53.0, 63.0, 113.0, 56.0, 64.0, 116.0)

    init {
        on<RenderEvent.World> {
            if (shouldHighlightBlocks && colour.alpha > 0) {
                ctx.drawFilledBox(healerBox, colour, depth)
            }

            if (shouldHighlightBlocks && wireframe) {
                ctx.drawWireFrameBox(healerBox, lineColour, lineWidth.toFloat(), depth)
            }
        }

        on<ChatEvent.Packet> {
            when (message.noControlCodes) {
                "[BOSS] Goldor: You have done it, you destroyed the factory…" -> {
                    shouldHighlightBlocks = true
                }
                "[BOSS] Goldor: Necron, forgive me." -> {
                    shouldHighlightBlocks = false
                }
            }
        }
    }
}