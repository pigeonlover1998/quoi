package quoi.module.impl.misc.riftsolvers

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.CaveSpider
import net.minecraft.world.entity.monster.Slime
import net.minecraft.world.entity.monster.Zombie
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.utils.EntityUtils
import quoi.utils.EntityUtils.interpolatedBox
import quoi.utils.aabb
import quoi.utils.render.DrawContextUtils.rect
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawWireFrameBox
import quoi.utils.scaledHeight
import quoi.utils.scaledWidth

object CraftRoomSolver {
    private const val WALL_Z = -116.5

    private val craftRoomArea = AABB(
        -108.0, 51.0, -128.0,
        -117.0, 58.0, -106.0
    )

    fun onRenderWorld(ctx: WorldRenderContext, player: LocalPlayer) {
        if (player.z > -100.0 || player.z < -140.0) return

        ctx.drawWireFrameBox(BlockPos(-113, 52, -115).aabb, Colour.BROWN, depth = true)
        
        val entities = EntityUtils.getEntities<LivingEntity>(craftRoomArea) { !it.isDeadOrDying && it != player }

        entities.forEach { entity ->
            val colour = when (entity) {
                is Zombie -> Colour.BROWN
                is Slime -> Colour.GREEN
                is CaveSpider -> Colour.WHITE
                else -> return@forEach
            }

            val mirroredBox = mirrorBox(entity.interpolatedBox)

            ctx.drawFilledBox(mirroredBox, colour.withAlpha(0.5f))
        }
    }

    fun onContainer(ctx: GuiGraphics, player: LocalPlayer) {
//        if (mc.screen?.title?.string != "Craft Item") return
        if (player.z > -100.0 || player.z < -140.0) return

        val x = scaledWidth / 2
        val y = scaledHeight / 6 // not a good idea but idgaf

        val stick = ItemStack(Items.STICK)
        val string = ItemStack(Items.STRING)
        val slime = ItemStack(Items.SLIME_BALL)

        val rod = ItemStack(Items.FISHING_ROD)
        val bow = ItemStack(Items.BOW)
        val lead = ItemStack(Items.LEAD)

        val rodRecipe = arrayOf(
            stick,  null,  null,
            string, stick, null,
            string, null,  stick
        )

        val bowRecipe = arrayOf(
            string, stick, null,
            string, null,  stick,
            string, stick, null
        )

        val leadRecipe = arrayOf(
            null,   string, string,
            null,   slime,  string,
            string, null,   null
        )

        val hammerRecipe = arrayOf(
            lead, bow, null,
            bow,  rod, null,
            null, rod, null
        )

        ctx.drawRecipe(x - 158, y, rodRecipe)
        ctx.drawRecipe(x - 74,  y, bowRecipe)
        ctx.drawRecipe(x + 10,  y, leadRecipe)
        ctx.drawRecipe(x + 94,  y, hammerRecipe)
    }

    private fun GuiGraphics.drawRecipe(x: Int, y: Int, recipe: Array<ItemStack?>) {
        val padding = 4
        val size = 18 * 3

        rect(x - padding, y - padding, size + padding * 2, size + padding * 2, Colour.GREY.withAlpha(0.6f).rgb)

        for (row in 0..2) {
            for (col in 0..2) {
                val item = recipe[row * 3 + col] ?: continue
                val x = x + (col * 18) + 1
                val y = y + (row * 18) + 1

                rect(x, y, 16, 16, Colour.WHITE.withAlpha(0.1f).rgb)

                renderItem(item, x, y)
                renderItemDecorations(mc.font, item, x, y)
            }
        }
    }

    private fun mirrorBox(box: AABB): AABB {
        val maxZ = 2 * WALL_Z - box.minZ
        val minZ = 2 * WALL_Z - box.maxZ

        return AABB(
            box.minX, box.minY, minZ,
            box.maxX, box.maxY, maxZ
        )
    }
}