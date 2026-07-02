package quoi.module.impl.misc.inventory.impl

import net.minecraft.world.entity.LivingEntity
import quoi.api.abobaui.constraints.impl.size.Fill
import quoi.api.abobaui.dsl.inset
import quoi.api.abobaui.dsl.outlineBlock
import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.dsl.radius
import quoi.api.abobaui.dsl.size
import quoi.api.abobaui.dsl.withScale
import quoi.api.abobaui.elements.Element
import quoi.api.abobaui.elements.Layout.Companion.divider
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.module.impl.misc.inventory.Inventory
import quoi.module.settings.group.SettingGroup
import quoi.module.settings.impl.HudComponent
import quoi.utils.StringUtils.width
import quoi.utils.render.DrawContextUtils.drawEntity
import quoi.utils.render.DrawContextUtils.drawText
import quoi.utils.ui.hud.Hud

object InventoryHud : SettingGroup(Inventory, HudComponent("Inventory", Hud("Inventory", Inventory, false))) {

    private val playerModel by switch("Player model")

    private val hud = resizableHud("Inventory", colour = Colour.RGB(139, 139, 139).withAlpha(155), outline = Colour.RGB(250, 250, 250).withAlpha(155)) {
        block(
            size(if (playerModel) 488.px else 400.px, 136.px),
            colour = colour,
            5.radius()
        ) {
            row(inset(4f)) {
                column(gap = 4.px) {
                    for (row in 0..2) {
                        row(gap = 4.px) {
                            repeat(9) { col ->
                                val slotIndex = 9 + (row * 9 + col)

                                outlineBlock(
                                    size(40.px, 40.px),
                                    colour = outline,
                                    thickness = thickness,
                                    radius = 5.radius()
                                ) {
                                    object : Element(size(40.px, 40.px)) {
                                        init { usingCtx = true }
                                        override fun drawCtx() {
                                            val stack = player.inventory.getItem(slotIndex)
                                            if (stack.isEmpty) return
                                            withScale {
                                                ctx.pose().scale(2f, 2f)
                                                ctx.item(stack, 2, 2)
                                                if (stack.count > 1) {
                                                    val t = stack.count.toString()
                                                    ctx.drawText(t, 20 - t.width(), 20 - mc.font.lineHeight)
                                                }
                                            }
                                        }
                                    }.add()
                                }
                            }
                        }
                    }
                }

                if (playerModel) {
                    divider(4.px)
                    object : Element(size(Fill, Fill)) {
                        init { usingCtx = true }
                        override fun drawCtx() {
                            withScale {
                                ctx.drawEntity(player as LivingEntity, 0, 0, width.toInt(), height.toInt(), 30f, yaw = -45f to 45f)
                            }
                        }
                    }.add()
                }
            }
        }
    }.withSettings(::playerModel)

    init {
        @Suppress("unchecked_cast")
        (component as HudComponent<Hud>).hud = hud
    }
}