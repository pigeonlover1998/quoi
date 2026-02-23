package quoi.api.customtriggers.conditions

import quoi.QuoiMod.mc
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.TriggerContext
import quoi.config.TypeName
import quoi.utils.ThemeManager.theme
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB

@TypeName("player_position")
class PositionCondition(var aabb: AABB = AABB(BlockPos(0, 0, 0))) : TriggerCondition {

    override fun matches(ctx: TriggerContext): Boolean {
        val player = mc.player ?: return false
        return aabb.intersects(player.boundingBox)
    }

    override fun displayString(): String {
        val x = (aabb.minX + aabb.maxX) / 2.0
        val y = (aabb.minY + aabb.maxY) / 2.0
        val z = (aabb.minZ + aabb.maxZ) / 2.0
        val size = "${aabb.maxX - aabb.minX}x${aabb.maxY - aabb.minY}x${aabb.maxZ - aabb.minZ}"
        return "Player at $x,$y,$z [$size]"
    }

    override fun ElementScope<*>.draw() = column(size(w = Copying)) {
        text(
            string = "Position",
            size = theme.textSize,
            colour = theme.textSecondary,
        )
    }
}