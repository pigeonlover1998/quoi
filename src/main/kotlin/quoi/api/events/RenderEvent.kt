package quoi.api.events

import quoi.api.events.core.CancellableEvent
import quoi.api.events.core.Event
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.entity.Entity as McEntity

abstract class RenderEvent {
    class World(val ctx: WorldRenderContext) : Event()
    class Overlay(val ctx: GuiGraphics, val tickCounter: DeltaTracker) : Event()
    class Entity(val entity: McEntity) : CancellableEvent()
//    class EntityModel(val entity: EntityLivingBase, val model: ModelBase, val limbSwing: Float, val limbSwingAmount: Float, val ageInTicks: Float, val headYaw: Float, val headPitch: Float, val scaleFactor: Float) : Event()

//    abstract class Entity { // todo
//        class Pre(val entity: net.minecraft.entity.Entity, val matrices: MatrixStack, val vertex: VertexConsumerProvider?, val light: Int, val ci: CallbackInfo) : CancellableEvent()
//        class Post(val entity: net.minecraft.entity.Entity, val matrices: MatrixStack, val vertex: VertexConsumerProvider?, val light: Int, val ci: CallbackInfo) : Event()
//    }
//
//    abstract class Player { // todo
//        class Pre(val entity: PlayerEntityRenderState, val matrices: MatrixStack) : CancellableEvent()
//    }
}