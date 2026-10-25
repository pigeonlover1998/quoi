package quoi.module.impl.misc.rift.dreadfarm.impl

import kotlinx.coroutines.delay
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import quoi.api.abobaui.dsl.ms
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.BlockEvent
import quoi.api.events.PacketEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.api.events.core.onAsync
import quoi.api.pathfinding.impl.WalkPathfinder
import quoi.module.impl.misc.rift.dreadfarm.DreadFarm
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.ChatUtils.modMessage
import quoi.utils.EntityUtils.distanceTo
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.WorldUtils.nearbyBlocks
import quoi.utils.WorldUtils.state
import quoi.utils.aabb
import quoi.utils.addVec
import quoi.utils.blockPos
import quoi.utils.distanceTo2DSqr
import quoi.utils.equal
import quoi.utils.floored
import quoi.utils.getDirection
import quoi.utils.isPathClear
import quoi.utils.render.drawLine
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.MovementUtils
import quoi.utils.skyblock.player.MovementUtils.cancelMovementTask
import quoi.utils.skyblock.player.MovementUtils.moveTo
import quoi.utils.skyblock.player.MovementUtils.stop
import quoi.utils.skyblock.player.PlayerUtils.eyePosition
import quoi.utils.skyblock.player.RotationUtils.rotateSmoothly
import quoi.utils.vec3

/**
 * modified SkyHanni (LGPL-2.1) (c) hannibal002
 * original: https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/features/rift/area/dreadfarm/RiftWiltedBerberisHelper.kt
 */
@Suppress("unused_expression")
object WiltedBerberis : ToggleableGroup(DreadFarm, "Wilted berberis") {

    private object HighlightSequence : ToggleableGroup(this@WiltedBerberis, "Highlight sequence") {
        private val style = highlight(colour = null, fillColour = null, glow = false)
        private val alpha by slider("Alpha", 100, 0, 255).childOf(style.component) { style.style == "Filled box" }

        init {
            on<RenderEvent.World> {
                if (!wand) return@on

                val seq = currentSequence ?: return@on
                val curr = seq.currentTarget

                if (curr != null) {
                    if (seq.rendering && !seq.away) {
                        ctx.draw(curr.aabb, Colour.GREEN)
                        seq.nextTarget?.let { ctx.draw(it.aabb, Colour.YELLOW) }
                        seq.thirdTarget?.let { ctx.draw(it.aabb, Colour.RED) }
                    }
                } else seq.particle?.let { ctx.renderParticleBerberis(it) }
            }
        }

        private fun LevelRenderContext.renderParticleBerberis(berberis: WiltedBerberis) {
            if (berberis.y == 0.0) return
            val pos = Vec3(berberis.position.x - 0.5, berberis.y, berberis.position.z - 0.5)

            if (!berberis.moving) {
                draw(pos.aabb, Colour.YELLOW)
            } else {
                draw(pos.aabb, Colour.WHITE)

                berberis.previous?.let { prev ->
                    val old = Vec3(prev.x - 0.5, berberis.y, prev.z - 0.5)
                    draw(old.aabb, Colour.GREY.withAlpha(alpha))
                    drawLine(listOf(old.add(0.5, 0.0, 0.5), pos.add(0.5, 0.0, 0.5)), Colour.WHITE)
                }
            }
        }

        private fun LevelRenderContext.draw(box: AABB, colour: Colour) =
            style.draw(this, box, overrideColour = colour, overrideFillColour = colour.withAlpha(alpha))
    }

    /*private object Auto : ToggleableGroup(this@WiltedBerberis, "Auto") { // maybe pathfind idk

        private val toggle by keybind("Toggle").onPress {
            if (!wand) return@onPress modMessage("no wand in hand")
            active = !active
            modMessage(active)

            if (!active) {
//                player.stop()
                cancelMovementTask()
                currentBush = null
            }
        }

        private var active = false
        private var currentBush: BlockPos? = null

        init {
            onAsync<TickEvent.End> {
                val seq = currentSequence ?: return@onAsync
                val bush = seq.activeTarget ?: return@onAsync

                if (bush == currentBush) return@onAsync

                val target = bush
                    .nearbyBlocks(4f) { it.state.block == Blocks.FARMLAND *//*&& isPathClear(it.vec3, bush.above().center)*//* }
                    .minByOrNull { it.center.distanceTo(player.position()) }?.center ?: return@onAsync

            }
        }

        override val running: Boolean
            get() = super.running && active && wand
    }*/

    private var wand = false
    private var currentSequence: BerberisSequence? = null

    // https://github.com/hannibal002/SkyHanni-REPO/blob/main/constants/rift/WiltedBerberisLocations.json
    private val sequences = listOf(
        BerberisSequence(Vec3(-29.0, 71.0, -179.0), 12),
        BerberisSequence(Vec3(-65.0, 72.0, -184.0), 15),
        BerberisSequence(Vec3(-79.0, 72.0, -162.0), 19),
        BerberisSequence(Vec3(-69.0, 71.0, -134.0), 21),
        BerberisSequence(Vec3(-33.0, 70.0, -140.0), 36),
        BerberisSequence(Vec3(-49.0, 69.0, -123.0), 8),
    )

    init {
        HighlightSequence
//        Auto

        on<TickEvent.End> {
            wand = player.mainHandItem.skyblockId == "FARMING_WAND"

            currentSequence = nearestSequence(player.position())

            for (seq in sequences) {
                if (seq.particle != null && System.currentTimeMillis() - seq.particle!!.lastTime > 500L) {
                    seq.particle = null
                }
                if (seq.sequence.isEmpty()) continue
                updateProximity(seq)
            }
        }

        on<PacketEvent.Received, ClientboundLevelParticlesPacket> {
            if (!wand) return@on

            val type = packet.particle.type
            if (type != ParticleTypes.FIREWORK && type != ParticleTypes.HAPPY_VILLAGER) return@on

            val pos = Vec3(packet.x, packet.y, packet.z)

            if (type == ParticleTypes.HAPPY_VILLAGER)
                return@on scheduleTask(1) { handleRespawnParticle(pos) }

            val seq = nearestSequence(pos) ?: return@on
            val berberis = seq.particle
            if (berberis == null) seq.particle = WiltedBerberis(pos)
            else berberis.updateParticlePosition(pos)
        }

        on<BlockEvent.Destroy.Start> {
            if (pos.state.block != Blocks.DEAD_BUSH) return@on
            advanceAt(pos)
        }

        on<BlockEvent.Update> {
            if (old.block != Blocks.DEAD_BUSH || updated.block != Blocks.AIR) return@on
            advanceAt(pos)
        }

        on<WorldEvent.Change> {
            wand = false
            currentSequence = null
        }
    }

    private fun advanceAt(pos: BlockPos) {
        for (seq in sequences) {
            if (!seq.rendering || seq.away) continue
            if (seq.isAtCurrent(pos)) {
                seq.advance()
                break
            }
        }
    }

    private fun WiltedBerberis.updateParticlePosition(location: Vec3) {
        val isMoving = !position.equal(location)

        if (isMoving) {
            if (position.distanceToSqr(location) > 9.0) {
                previous = null
                moving = true
            }
            if (!moving) {
                previous = position
            }
        } else y = location.y - 1

        moving = isMoving
        position = location
        lastTime = System.currentTimeMillis()
    }

    private fun handleRespawnParticle(location: Vec3) {
        val roundedLocation = location.floored
        if (roundedLocation.addVec(y = -1).blockPos.state.block != Blocks.FARMLAND) return

        val seq = nearestSequence(roundedLocation) ?: return
        if (seq.valid) return

        val currentTime = System.currentTimeMillis()

        if (currentTime - seq.lastParticleTime > 3_000L) seq.sequence.clear()
        seq.lastParticleTime = currentTime

        if (seq.sequence.lastOrNull()?.equal(roundedLocation) == true) return
        seq.sequence.add(roundedLocation)

        if (seq.sequence.size > seq.expectedCount) {
            seq.sequence.clear()
            seq.sequence.add(roundedLocation)
            return
        }

        if (seq.sequence.size == seq.expectedCount) {
            seq.valid = true
            seq.rendering = true
            modMessage("§aBerberis respawn sequence learnt!")
        }
    }

    private fun nearestSequence(location: Vec3): BerberisSequence? =
        sequences.filter { it.centre.distanceTo2DSqr(location) < 400.0 }
            .minByOrNull { it.centre.distanceTo2DSqr(location) }

    private fun validatePartialSequence(seq: BerberisSequence) {
        if (seq.sequence.any { it.blockPos.state.block != Blocks.DEAD_BUSH }) seq.sequence.clear()
    }

    private fun validateSequence(seq: BerberisSequence) {
        for ((index, location) in seq.sequence.withIndex()) {
            val bush = location.blockPos.state.block == Blocks.DEAD_BUSH
            if (bush != (index >= seq.currentIndex)) {
                seq.invalidate()
                return
            }
        }
    }

    private fun updateProximity(seq: BerberisSequence) {
        if (seq.centre.distanceTo2DSqr(player.position()) > 400.0) {
            seq.away = true
            return
        }
        if (seq.away) {
            seq.away = false
            if (seq.valid) validateSequence(seq) else validatePartialSequence(seq)
        }
    }

    private class WiltedBerberis(var position: Vec3) {
        var previous: Vec3? = null
        var moving = false
        var y = 0.0
        var lastTime = System.currentTimeMillis()
    }

    private class BerberisSequence(val centre: Vec3, val expectedCount: Int) {
        val sequence = mutableListOf<Vec3>()

        var valid = false
        var rendering = false
        var away = false
        var currentIndex = 0
        var previousTarget: Vec3? = null
        var lastParticleTime = 0L

        var particle: WiltedBerberis? = null
        val currentTarget get() = sequence.getOrNull(currentIndex)
        val nextTarget get() = sequence.getOrNull(currentIndex + 1)
        val thirdTarget get() = sequence.getOrNull(currentIndex + 2)

        val activeTarget: BlockPos?
            get() {
                currentTarget?.let { return it.blockPos.below(2) }
                val p = particle ?: return null
                return Vec3(p.position.x, p.y - 1, p.position.z).blockPos
            }

        fun reset() {
            sequence.clear()
            valid = false
            rendering = false
            currentIndex = 0
            previousTarget = null
            lastParticleTime = 0L
        }

        fun invalidate() {
            reset()
            modMessage("§cBerberis sequence invalid, falling back to particle mode")
        }

        fun advance() {
            val current = currentTarget
            currentIndex++
            if (currentIndex >= sequence.size) {
                val wasRendering = rendering && valid
                reset()
                rendering = wasRendering
            } else {
                previousTarget = current
            }
        }

        fun isAtCurrent(location: BlockPos) = currentTarget?.blockPos == location
    }

}