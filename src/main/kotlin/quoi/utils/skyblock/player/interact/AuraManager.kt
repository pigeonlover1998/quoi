package quoi.utils.skyblock.player.interact

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.*
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.mc
import quoi.annotations.Init
import quoi.api.colour.Colour
import quoi.api.colour.lerpColour
import quoi.api.colour.withAlpha
import quoi.api.events.*
import quoi.api.events.core.EventBus.on
import quoi.module.impl.misc.Test
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.WorldUtils.shape
import quoi.utils.WorldUtils.state
import quoi.utils.copy
import quoi.utils.getHitResult
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawWireFrameBox
import quoi.utils.startPrediction

@Init
object AuraManager {
    private val blockTasks = mutableListOf<BlockInteract>()
    private var interactBlockCd = 0

    private val entityTasks = mutableListOf<EntityInteract>()
    private var interactEntityCd = 0

    private var mineTarget: MineTarget? = null
    private var mineBlockCd = 0

    private val recentClicks = mutableListOf<Vec3>()

    init {
        on<WorldEvent.Change> {
            blockTasks.clear()
            entityTasks.clear()
            recentClicks.clear()
            interactBlockCd = 20
            interactEntityCd = 20

            mineTarget = null
            mineBlockCd = 0
        }

        on<TickEvent.Start> {
            if (interactBlockCd == 0 && blockTasks.isNotEmpty()) {
                blockTasks.removeFirst().execute()
            }
            if (interactEntityCd == 0 && entityTasks.isNotEmpty()) {
                entityTasks.removeFirst().execute()
            }

            mineTarget?.let { target ->
                target.onTick(mineBlockCd)
                if (target.finished) {
                    mineTarget = null
                    mineBlockCd = 6
                }
            }
        }

        on<TickEvent.End> {
            if (interactBlockCd > 0) interactBlockCd--
            if (interactEntityCd > 0) interactEntityCd--
            if (mineBlockCd > 0) mineBlockCd--
        }

        on<PacketEvent.Sent> {
            if (packet is ServerboundUseItemOnPacket) interactBlockCd = 1
            if (packet is ServerboundInteractPacket) interactEntityCd = 1
        }

        on<PacketEvent.Received> {
            when (packet) {
                is ClientboundBlockUpdatePacket -> mineTarget?.onBlockUpdate(packet.pos, packet.blockState)
                is ClientboundSectionBlocksUpdatePacket -> if (mineTarget != null) packet.runUpdates(mineTarget!!::onBlockUpdate)
                is ClientboundBlockDestructionPacket -> mc.execute {
                    val target = mineTarget ?: return@execute
                    if (target.custom && target.pos == packet.pos) {
                        target.progress = (packet.progress / 10f).coerceIn(0f, 1f)
                    }
                }
            }
        }

        on<BlockEvent.Destroy.Start> {
            if (mineTarget != null) cancel()
        }

        on<RenderEvent.World> {
            recentClicks.forEach { vec ->
                val aabb = AABB(
                    vec.x - 0.05, vec.y - 0.05, vec.z - 0.05,
                    vec.x + 0.05, vec.y + 0.05, vec.z + 0.05
                )
                ctx.drawWireFrameBox(aabb, Colour.BLUE, depth = false)
            }

            mineTarget?.let { target ->
                val state = target.pos.state
                if (state.isAir) return@let

                val shape = target.pos.shape
                if (shape.isEmpty) return@let

                var aabb = shape.bounds().move(target.pos)
                val progress = target.progress.coerceIn(0f, 1f)

                val col = when {
                    progress < 0.5f -> lerpColour(Colour.RED, Colour.ORANGE, progress * 2f)
                    else -> lerpColour(Colour.ORANGE, Colour.GREEN, (progress - 0.5f) * 2f)
                }

                aabb = aabb.copy(maxY = aabb.minY + (aabb.ysize * progress))

                ctx.drawFilledBox(aabb, col.withAlpha(0.4f), depth = false)
                ctx.drawWireFrameBox(aabb, col, thickness = 3f, depth = false)
            }
        }
    }

    fun interactBlock(pos: BlockPos, force: Boolean = false) {
        val task = BlockInteract(pos, force)
        if (interactBlockCd > 0) blockTasks.add(task) else task.execute()
    }

    fun interactEntity(entity: Entity, action: AuraAction) {
        val task = EntityInteract(entity, action)
        if (interactEntityCd > 0) entityTasks.add(task) else task.execute()
    }

    fun breakBlock(pos: BlockPos, immediate: Boolean = false, custom: Boolean = false, swing: Boolean = true) {
        if (mineTarget?.pos == pos) return

        val hitResult = pos.getHitResult() ?: return
        debugBox(hitResult.location)

        if (immediate) {
            mc.gameMode?.startPrediction { sequence ->
                ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    pos,
                    hitResult.direction,
                    sequence,
                )
            }
            if (swing) mc.player?.swing(InteractionHand.MAIN_HAND)
            mineBlockCd = 6
            return
        }
        mineTarget = MineTarget(pos, hitResult.direction, custom, swing)
    }

    fun debugBox(vec3: Vec3) {
        if (!Test.auraDebug) return
        recentClicks.add(vec3)
        scheduleTask(10) { recentClicks.remove(vec3) }
    }
}

enum class AuraAction { INTERACT, INTERACT_AT }