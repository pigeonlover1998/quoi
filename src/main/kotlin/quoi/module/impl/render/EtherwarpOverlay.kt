package quoi.module.impl.render

import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.RenderEvent
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.aabb
import quoi.utils.eyeHeight
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawWireFrameBox
import quoi.utils.skyblock.ItemUtils.extraAttributes
import quoi.utils.skyblock.ItemUtils.skyblockId
import quoi.utils.traverseVoxels

object EtherwarpOverlay : Module (
    "Etherwarp Overlay",
    desc = "Renders a box at the location where the etherwarp is going to be at."
) {
    private val validColour by colourPicker("Valid colour", Colour.GREEN.withAlpha(60), true)
    private val invalidColour by colourPicker("Invalid colour", Colour.RED.withAlpha(60), true)
    private val wireframe by switch("Show outline")
    private val validLineColour by colourPicker("Valid colour", Colour.GREEN.withAlpha(60), true).json("Valid outline colour").childOf(::wireframe)
    private val invalidLineColour by colourPicker("Invalid colour", Colour.RED.withAlpha(60), true).json("Invalid outline colour").childOf(::wireframe)
    private val lineWidth by slider("Outline width", 2.0, 0.1, 10.0, 0.1).childOf(::wireframe)
    private val depth by switch("Depth check")

    private var cachedItem: ItemStack? = null
    private var dist = 0.0
    private var conduit = false

    init {
        on<RenderEvent.World> {
            val heldItem = player.mainHandItem
            if (heldItem !== cachedItem) {
                cachedItem = heldItem
                val attributes = heldItem.extraAttributes

                conduit = heldItem.skyblockId == "ETHERWARP_CONDUIT"
                val merge = attributes?.getInt("ethermerge")?.orElse(0) == 1

                if (conduit || merge) {
                    val tuners = attributes?.getInt("tuned_transmission")?.orElse(0) ?: 0
                    dist = 57.0 + tuners
                } else {
                    dist = 0.0
                }
            }

            if (dist == 0.0 || (!conduit && !player.isShiftKeyDown)) return@on

            val start = Vec3(player.xo, player.yo + player.eyeHeight(), player.zo)
            val end = start.add(player.lookAngle.scale(dist))

            val result = traverseVoxels(start, end, true)
            val box = result.pos?.aabb ?: return@on

            val fillCol = if (result.succeeded) validColour else invalidColour
            if (fillCol.alpha > 0) {
                ctx.drawFilledBox(box, fillCol, depth)
            }

            if (wireframe) {
                val lineCol = if (result.succeeded) validLineColour else invalidLineColour
                ctx.drawWireFrameBox(box, lineCol, lineWidth.toFloat(), depth)
            }
        }
    }
}