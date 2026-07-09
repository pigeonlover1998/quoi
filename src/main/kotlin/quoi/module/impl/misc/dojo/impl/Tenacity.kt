package quoi.module.impl.misc.dojo.impl

import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.api.events.EntityEvent
import quoi.api.events.PacketEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.api.pathfinding.impl.WalkPathfinder
import quoi.module.impl.misc.dojo.Dojo
import quoi.module.impl.misc.dojo.DojoType
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.BlockPos
import quoi.utils.WorldUtils.nearbyBlocks
import quoi.utils.WorldUtils.solid
import quoi.utils.WorldUtils.walkable
import quoi.utils.aabb
import quoi.utils.addVec
import quoi.utils.floorPos
import quoi.utils.skyblock.player.MovementUtils.cancelMovementTask
import quoi.utils.skyblock.player.MovementUtils.moveTo
import quoi.utils.skyblock.player.MovementUtils.moving

object Tenacity : ToggleableGroup(Dojo, "Tenacity", subarea = "dojo arena") {

    private val highlight by switch("Highlight trajectories")
    private val style = highlight(glow = false).childOf(::highlight)
    private val line = tracer("Line", colour = null).childOf(::highlight)

//    private val auto by switch("Auto dodge")

    private val fireballs = mutableListOf<Fireball>()

    init {
        on<EntityEvent.Spawn> {
            if (entity !is ArmorStand) return@on
            if (Dojo.centre.distanceToSqr(entity.position()) > 625) return@on

            if (fireballs.none { it.entity.id == entity.id }) {
                fireballs.add(Fireball(entity, entity.position()))
            }
        }

        on<PacketEvent.Received, ClientboundLevelParticlesPacket> {
            if (packet.particle.type != ParticleTypes.FLAME) return@on

            val pos = Vec3(packet.x, packet.y, packet.z)
            var nearest: Fireball? = null
            var closestDist = 50.0

            fireballs.forEach {
                val dist = it.entity.position().distanceTo(pos)
                if (dist < closestDist) {
                    nearest = it
                    closestDist = dist
                }
            }

            if (nearest != null && nearest.end == null) {
                nearest.offset = pos.subtract(nearest.entity.position())
            }
        }

        on<TickEvent.End> {
            penis()
//            if (!auto) return@on
//            val dangerBlocks = fireballs.flatMapTo(mutableSetOf()) { it.blocks }
//
//            val floor = player.position().floorPos
//
//            if (floor !in dangerBlocks) return@on
//            // todo find optimal block and move
            // no fucking idea how legits do this shit. it's cancer.
        }

        on<RenderEvent.World> {
            if (!highlight) return@on
            fireballs.forEach {
                val start = it.start ?: return@forEach
                val end = it.end ?: return@forEach

                line.draw(ctx, listOf(start, end), style.outline)

                it.blocks.forEach { pos ->
                    style.draw(ctx, pos.aabb)
                }

                it.box?.let { box -> style.draw(ctx, box) }
            }
        }
    }

    private fun penis() = fireballs.removeIf {
        if (it.entity.isRemoved) return@removeIf true

        if (it.end == null && it.entity.position() != it.spawn) {
            val start = it.spawn.add(it.offset)
            it.start = start

            val dir = it.entity.position().subtract(it.spawn)
            val hit = level.clip(ClipContext(start, start.add(dir.scale(100.0)), ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, it.entity))
            it.end = hit.location
            it.box = AABB.ofSize(hit.location, 3.0, 0.1, 3.0)

//            val blocks = BlockPos.betweenClosed(bp.x - 1, 98, bp.z - 1, bp.x + 1, 98, bp.z + 1).toMutableSet()
            val blocks = mutableSetOf<BlockPos>()
//            val bp = hit.blockPos
//            for (x in bp.x - 1..bp.x + 1)
//                for (z in bp.z - 1..bp.z + 1)
//                    blocks += BlockPos(x, 98, z)

            val path = hit.location.subtract(start)
            val distance = path.length()

            if (distance > 0.1) { // trajectory path
                val step = path.scale(0.5 / distance)
                val perp = Vec3(-path.z, 0.0, path.x).normalize().scale(0.5)

                var p = start
                repeat((distance * 2).toInt()) {
                    if (p.y <= 101.5) {
                        blocks += BlockPos(p.x, 98, p.z)
                        blocks += BlockPos(p.x + perp.x, 98, p.z + perp.z)
                        blocks += BlockPos(p.x - perp.x, 98, p.z - perp.z)
                    }
                    p = p.add(step)
                }
            }

            it.blocks = blocks
        } else if (it.end != null && it.start != null) { // cleanu p path
            val start = it.start!!
            val end = it.end!!
            val delta = end.subtract(start)
            val dir = delta.normalize()
            val travelled = it.entity.position().subtract(start).dot(dir).coerceAtMost(delta.length())

            it.blocks.removeIf { b ->
                Vec3(b.x + 0.5, b.y + 0.5, b.z + 0.5).subtract(start).dot(dir) < travelled - 1.5
            }
        }
        false
    }

    override fun onDisable() {
        fireballs.clear()
        cancelMovementTask()
    }

    private data class Fireball(
        val entity: ArmorStand,
        val spawn: Vec3,
        var offset: Vec3 = Vec3.ZERO,
        var start: Vec3? = null,
        var end: Vec3? = null,
        var blocks: MutableSet<BlockPos> = mutableSetOf(),
        var box: AABB? = null
    )

    override val running: Boolean
        get() = super.running && Dojo.dojoType == DojoType.TENACITY
}