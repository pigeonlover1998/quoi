package quoi.utils.skyblock.player

import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.api.events.PacketEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventBus.on
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.render.drawWireFrameBox
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import quoi.annotations.Init
import quoi.utils.eyePosition

// modified https://github.com/Hypericat/NoobRoutes/blob/main/src/main/kotlin/noobroutes/utils/AuraManager.kt
@Init
object AuraManager {
    class EntityAura(val entity: Entity, val action: AuraAction)
    class BlockAura(val pos: BlockPos, val force: Boolean, val callback: () -> Unit)

    private val queuedBlocks = mutableListOf<BlockAura>()
    private var clickBlockCooldown = 0

    private val queuedEntityClicks = mutableListOf<EntityAura>()
    private var clickEntityCooldown = 0

    private val recentClicks = mutableListOf<Vec3>()

    init {
        on<WorldEvent.Change> {
            queuedBlocks.clear()
            queuedEntityClicks.clear()
            recentClicks.clear()
            clickBlockCooldown = 20
            clickEntityCooldown = 20
        }

        on<TickEvent.Start> {
            if (clickBlockCooldown == 0) {
                queuedBlocks.firstOrNull()?.let { clickBlock(it, true) }
            }
            if (clickEntityCooldown == 0) {
                queuedEntityClicks.firstOrNull()?.let { clickEntity(it, true) }
            }
        }

        on<TickEvent.End> {
            if (clickBlockCooldown > 0) clickBlockCooldown--
            if (clickEntityCooldown > 0) clickEntityCooldown--
        }

        on<PacketEvent.Sent> {
            if (packet is ServerboundUseItemOnPacket) clickBlockCooldown = 1
            if (packet is ServerboundInteractPacket) clickEntityCooldown = 1
        }

        on<RenderEvent.World> {
            recentClicks.forEach { vec ->

                val aabb = AABB(
                    vec.x - 0.05, vec.y - 0.05, vec.z - 0.05,
                    vec.x + 0.05, vec.y + 0.05, vec.z + 0.05
                )

                ctx.drawWireFrameBox(aabb, Colour.BLUE, depth = false)
            }
        }
    }

    fun interactBlock(pos: BlockPos, force: Boolean = false, callback: () -> Unit = { }) {
        val blockAura = BlockAura(pos, force, callback)
        if (clickBlockCooldown > 0) {
            queuedBlocks.add(blockAura)
        } else {
            clickBlock(blockAura)
        }
    }

    fun interactBlock(x: Int, y: Int, z: Int, force: Boolean = false, callback: () -> Unit = { }) {
        interactBlock(BlockPos(x, y, z), force, callback)
    }

    fun auraEntity(entity: Entity, action: AuraAction) {
        val entityAura = EntityAura(entity, action)
        if (clickEntityCooldown > 0) {
            queuedEntityClicks.add(entityAura)
        } else {
            clickEntity(entityAura)
        }
    }

    fun breakBlock(pos: BlockPos, swing: Boolean = true) {
        val hitResult = getHitResult(pos, false) ?: return
        debugBox(hitResult.location)

        mc.connection?.send(
            ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                pos,
                hitResult.direction
            )
        )
        if (swing) mc.player?.swing(InteractionHand.MAIN_HAND)
    }

    private fun clickBlock(aura: BlockAura, removeFirst: Boolean = false) {
        val hitResult = getHitResult(aura.pos, aura.force)

        if (hitResult == null) {
            aura.callback()
            if (removeFirst) queuedBlocks.removeFirst()
            return
        }

        debugBox(hitResult.location)

        mc.connection?.send(
            ServerboundUseItemOnPacket(
                InteractionHand.MAIN_HAND,
                hitResult,
                0
            )
        )

        if (removeFirst) queuedBlocks.removeFirst()
    }

    private fun clickEntity(entityAura: EntityAura, removeFirst: Boolean = false) {
        if (removeFirst) queuedEntityClicks.removeFirst()
        val player = mc.player ?: return
        val entity = entityAura.entity
        if (player.eyePosition().distanceTo(entity.position()) > 5) return

        if (entityAura.action == AuraAction.INTERACT_AT) {
            val expand = entity.pickRadius.toDouble()
            val boundingBox = entity.boundingBox.inflate(expand)

            val clipResult = boundingBox.clip(player.eyePosition(), boundingBox.center)

            if (clipResult.isPresent) {
                val hitVec = clipResult.get()

                mc.connection?.send(
                    ServerboundInteractPacket.createInteractionPacket(
                        entity,
                        player.isShiftKeyDown,
                        InteractionHand.MAIN_HAND,
                        hitVec.subtract(entity.position())
                    )
                )

                mc.connection?.send(
                    ServerboundInteractPacket.createInteractionPacket(
                        entity,
                        player.isShiftKeyDown,
                        InteractionHand.MAIN_HAND
                    )
                )

                debugBox(hitVec)
            }
        } else {
            mc.connection?.send(
                ServerboundInteractPacket.createInteractionPacket(
                    entity,
                    player.isShiftKeyDown,
                    InteractionHand.MAIN_HAND
                )
            )
            debugBox(entity.boundingBox.center)
        }
    }

    private fun getHitResult(pos: BlockPos, force: Boolean): BlockHitResult? {
        val level = mc.level ?: return null
        val player = mc.player ?: return null

        var shape = level.getBlockState(pos).getShape(level, pos)
        if (shape.isEmpty && force) shape = Shapes.block()
        if (shape.isEmpty) return null

        val centre = shape.bounds().center.add(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
        return shape.clip(player.eyePosition(), centre, pos)
            ?: BlockHitResult(centre, Direction.UP, pos, false)
    }


    private fun debugBox(vec3: Vec3) {
//        return // comm to debug
        recentClicks.add(vec3)
        scheduleTask(10) { recentClicks.remove(vec3) }
    }
}

enum class AuraAction { INTERACT, INTERACT_AT }