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
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes

// modified https://github.com/Hypericat/NoobRoutes/blob/main/src/main/kotlin/noobroutes/utils/AuraManager.kt
object AuraManager {
    class EntityAura(val entity: Entity, val action: Action)
    class BlockAura(val pos: BlockPos, val force: Boolean, val callback: () -> Unit)

    private val queuedBlocks = mutableListOf<BlockAura>()
    private var clickBlockCooldown = 0

    private val queuedEntityClicks = mutableListOf<EntityAura>()
    private var clickEntityCooldown = 0

    private val recentClicks = mutableListOf<Vec3>()

    fun init() {
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

    fun auraBlock(pos: BlockPos, force: Boolean = false, callback: () -> Unit = { }) {
        val blockAura = BlockAura(pos, force, callback)
        if (clickBlockCooldown > 0) {
            queuedBlocks.add(blockAura)
        } else {
            clickBlock(blockAura)
        }
    }

    fun auraBlock(x: Int, y: Int, z: Int, force: Boolean = false, callback: () -> Unit = { }) {
        auraBlock(BlockPos(x, y, z), force, callback)
    }

    fun auraEntity(entity: Entity, action: Action) {
        val entityAura = EntityAura(entity, action)
        if (clickEntityCooldown > 0) {
            queuedEntityClicks.add(entityAura)
        } else {
            clickEntity(entityAura)
        }
    }

    fun clickBlock(aura: BlockAura, removeFirst: Boolean = false) {
        val level = mc.level ?: return
        val player = mc.player ?: return

        var shape = level.getBlockState(aura.pos).getShape(level, aura.pos)

        if (shape.isEmpty && aura.force) {
            shape = Shapes.block()
        }

        if (shape.isEmpty) {
            aura.callback()
            if (removeFirst) queuedBlocks.removeFirst()
            return
        }

        val centre = shape.bounds().center.add(aura.pos.x.toDouble(), aura.pos.y.toDouble(), aura.pos.z.toDouble())
        val eyePos = player.eyePosition

        val hitResult = shape.clip(eyePos, centre, aura.pos)
            ?: BlockHitResult(centre, Direction.UP, aura.pos, false)

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

    fun clickEntity(entityAura: EntityAura, removeFirst: Boolean = false) {
        if (removeFirst) queuedEntityClicks.removeFirst()
        val player = mc.player ?: return
        val entity = entityAura.entity
        if (player.eyePosition.distanceTo(entity.position()) > 5) return

        if (entityAura.action == Action.INTERACT_AT) {
            val expand = entity.pickRadius.toDouble()
            val boundingBox = entity.boundingBox.inflate(expand)

            val clipResult = boundingBox.clip(player.eyePosition, boundingBox.center)

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

    private fun debugBox(vec3: Vec3) {
        return // comm to debug
        recentClicks.add(vec3)
        scheduleTask(10) { recentClicks.remove(vec3) }
    }
}

enum class Action { INTERACT, INTERACT_AT }