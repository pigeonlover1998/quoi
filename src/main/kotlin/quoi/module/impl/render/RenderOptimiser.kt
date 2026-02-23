package quoi.module.impl.render

import quoi.api.events.GuiEvent
import quoi.api.events.PacketEvent
import quoi.api.skyblock.dungeon.Dungeon
import quoi.mixins.accessors.TexturedButtonWidgetAccessor
import quoi.module.Module
import quoi.module.settings.impl.BooleanSetting
import quoi.utils.skyblock.ItemUtils.texture
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.components.ImageButton
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot

object RenderOptimiser : Module(
    "Render Optimiser",
    desc = "Various render optimisation features."
) {
    @JvmStatic val disableTextShadow by BooleanSetting("Disable text shadow", desc = "Disables text shadows in hud elements.")
    @JvmStatic val containerTextShadow by BooleanSetting("Container text shadow", desc = "Renders text in containers with shadow.")
    @JvmStatic val disableFog by BooleanSetting("Disable fog", desc = "Disables fog rendering.")

    private val hideFallingBlocks by BooleanSetting("Hide falling blocks", desc = "Disables falling blocks rendering.")
    private val hideLightning by BooleanSetting("Hide lightning", desc = "Disables lightning rendering.")
    private val hideWeaver by BooleanSetting("Hide soul weaver", desc = "Disables soul weaver skulls rendering.")
    private val hideFairy by BooleanSetting("Hide healer fairy", desc = "Disables healer fairy rendering.")
    private val hideRecipeBook by BooleanSetting("Hide recipe book", desc = "Disables recipe book rendering.")
    private val hideBlindness by BooleanSetting("Hide blindness", desc = "Disabled blindness effect rendering.")
    @JvmStatic val hideFire by BooleanSetting("Hide fire overlay", desc = "Disables fire overlay rendering.")

    @JvmStatic val fullBright by BooleanSetting("Full bright", desc = "Makes dark places bright.")

    private const val HEALER_FAIRY_TEXTURE = "ewogICJ0aW1lc3RhbXAiIDogMTcxOTQ2MzA5MTA0NywKICAicHJvZmlsZUlkIiA6ICIyNjRkYzBlYjVlZGI0ZmI3OTgxNWIyZGY1NGY0OTgyNCIsCiAgInByb2ZpbGVOYW1lIiA6ICJxdWludHVwbGV0IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzJlZWRjZmZjNmExMWEzODM0YTI4ODQ5Y2MzMTZhZjdhMjc1MmEzNzZkNTM2Y2Y4NDAzOWNmNzkxMDhiMTY3YWUiCiAgICB9CiAgfQp9"
    private const val SOUL_WEAVER_TEXTURE = "eyJ0aW1lc3RhbXAiOjE1NTk1ODAzNjI1NTMsInByb2ZpbGVJZCI6ImU3NmYwZDlhZjc4MjQyYzM5NDY2ZDY3MjE3MzBmNDUzIiwicHJvZmlsZU5hbWUiOiJLbGxscmFoIiwic2lnbmF0dXJlUmVxdWlyZWQiOnRydWUsInRleHR1cmVzIjp7IlNLSU4iOnsidXJsIjoiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8yZjI0ZWQ2ODc1MzA0ZmE0YTFmMGM3ODViMmNiNmE2YTcyNTYzZTlmM2UyNGVhNTVlMTgxNzg0NTIxMTlhYTY2In19fQ=="


    init {
        on<PacketEvent.Received> {
            if (mc.player == null) return@on
            when(packet) {
                is ClientboundAddEntityPacket -> {
                    if (hideFallingBlocks && packet.type == EntityType.FALLING_BLOCK ||
                        hideLightning && packet.type == EntityType.LIGHTNING_BOLT) cancel()
                }
                is ClientboundSetEquipmentPacket -> {
                    if (!Dungeon.inDungeons) return@on
                    packet.slots.forEach { slot ->
                        if (slot.second.isEmpty) return@forEach
                        val texture = slot.second.texture ?: return@forEach

                        if (
                            (hideFairy && slot.first == EquipmentSlot.MAINHAND && texture == HEALER_FAIRY_TEXTURE) ||
                            (hideWeaver && slot.first == EquipmentSlot.HEAD && texture == SOUL_WEAVER_TEXTURE)
                        ) mc.execute { level.removeEntity(packet.entity, Entity.RemovalReason.DISCARDED) }
                    }
                }

                is ClientboundUpdateMobEffectPacket -> {
                    if (hideBlindness &&
                        packet.entityId == player.id &&
                        packet.effect == MobEffects.BLINDNESS) cancel()
                }

            }
        }

        on<GuiEvent.Open.Post> {
            if (!hideRecipeBook) return@on
            Screens.getButtons(screen)
                .filterIsInstance<ImageButton>()
                .firstOrNull { (it as TexturedButtonWidgetAccessor).textures == RecipeBookComponent.RECIPE_BUTTON_SPRITES }
                ?.visible = false
        }
    }

    @JvmStatic
    fun should(condition: Boolean): Boolean = this.enabled && condition // idkman
}