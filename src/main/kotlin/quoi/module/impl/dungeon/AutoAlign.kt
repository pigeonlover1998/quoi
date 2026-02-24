package quoi.module.impl.dungeon

import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon.inP3
import quoi.api.skyblock.dungeon.Dungeon.isDead
import quoi.api.skyblock.invoke
import quoi.module.Module
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

// Kyleen
object AutoAlign : Module(
    "Auto Align",
    desc = "Automatically completes arrows align device.",
    area = Island.Dungeon(inBoss = true)
) {

    private val deviceStandLocation = BlockPos(0, 120, 77)
    private val deviceCorner = BlockPos(-2, 120, 75)
    private val interactAtVec = Vec3(0.03125, 0.0, 0.0)
    private val recentClicks = HashMap<Int, Long>()
    private val persistentFrames = HashMap<Int, CachedFrame>()
    private data class CachedFrame(val entity: ItemFrame, var rotation: Int)

    private val solutions = listOf(
        listOf(7, 7, 7, 7, null, 1, null, null, null, null, 1, 3, 3, 3, 3, null, null, null, null, 1, null, 7, 7, 7, 1),
        listOf(null, null, null, null, null, 1, null, 1, null, 1, 1, null, 1, null, 1, 1, null, 1, null, 1, null, null, null, null, null),
        listOf(5, 3, 3, 3, null, 5, null, null, null, null, 7, 7, null, null, null, 1, null, null, null, null, 1, 3, 3, 3, null),
        listOf(null, null, null, null, null, null, 1, null, 1, null, 7, 1, 7, 1, 3, 1, null, 1, null, 1, null, null, null, null, null),
        listOf(null, null, 7, 7, 5, null, 7, 1, null, 5, null, null, null, null, null, null, 7, 5, null, 1, null, null, 7, 7, 1),
        listOf(7, 7, null, null, null, 1, null, null, null, null, 1, 3, 3, 3, 3, null, null, null, null, 1, null, null, null, 7, 1),
        listOf(5, 3, 3, 3, 3, 5, null, null, null, 1, 7, 7, null, null, 1, null, null, null, null, 1, null, 7, 7, 7, 1),
        listOf(7, 7, null, null, null, 1, null, null, null, null, 1, 3, null, 7, 5, null, null, null, null, 5, null, null, null, 3, 3),
        listOf(null, null, null, null, null, 1, 3, 3, 3, 3, null, null, null, null, 1, 7, 7, 7, 7, 1, null, null, null, null, null)
    )

    init {
        on<TickEvent.End> {
            if (!isDead) handleArrowAlign()
        }

        on<WorldEvent.Change> {
            persistentFrames.clear()
        }
    }

    private fun handleArrowAlign() {
        if (player.distanceToSqr(Vec3.atCenterOf(deviceStandLocation)) > 100) {
            persistentFrames.clear()
            return
        }

        val currentFrames = getCurrentFrames()
        val rotations = currentFrames.map { it?.rotation }

        val solution = solutions.find { sol ->
            !sol.indices.any { i -> (sol[i] == null) xor (rotations[i] == null) }
        } ?: return

        val framesWithIndices = currentFrames.mapIndexed { index, frame -> index to frame }
            .filter { it.second != null }
            .sortedBy { (_, frame) ->
                frame!!.entity.distanceToSqr(player)
            }

        for ((index, frame) in framesWithIndices) {
            if (frame == null) continue
            val entity = frame.entity

            if (entity.distanceToSqr(player) > 25) continue

            val targetRotation = solution[index] ?: continue
            var clicksNeeded = (targetRotation - frame.rotation + 8) % 8

            if (clicksNeeded <= 0) continue

            val framesNeedingClicks = currentFrames.filterIndexed { i, f ->
                f != null && solution[i] != null && (solution[i]!! - f.rotation + 8) % 8 > 0
            }.count()

            if (!inP3 && framesNeedingClicks <= 1) {
                clicksNeeded--
            }

            if (clicksNeeded > 0) {
                recentClicks[index] = System.currentTimeMillis()

                repeat(clicksNeeded) {
                    frame.rotation = (frame.rotation + 1) % 8

                    mc.connection?.send(
                        ServerboundInteractPacket.createInteractionPacket(
                            entity,
                            false,
                            InteractionHand.MAIN_HAND,
                            interactAtVec
                        )
                    )

                    mc.connection?.send(
                        ServerboundInteractPacket.createInteractionPacket(
                            entity,
                            false,
                            InteractionHand.MAIN_HAND
                        )
                    )
                }
                break
            }
        }
    }

    private fun getCurrentFrames(): List<CachedFrame?> {
        val scanBox = AABB(deviceCorner).expandTowards(5.0, 5.0, 5.0).inflate(1.0)
        val entities = level.getEntitiesOfClass(ItemFrame::class.java, scanBox) {
            it.item.item == Items.ARROW
        }

        val frameMap = entities.associateBy { frame ->
            val pos = frame.blockPosition()
            pos
        }

        val result = ArrayList<CachedFrame?>()
        val (startX, startY, startZ) = deviceCorner.let { Triple(it.x, it.y, it.z) }

        for (dz in 0 until 5) {
            for (dy in 0 until 5) {
                val index = dy + dz * 5

                if (persistentFrames.containsKey(index) &&
                    System.currentTimeMillis() - (recentClicks[index] ?: 0L) < 1000) {
                    result.add(persistentFrames[index])
                    continue
                }

                val targetPos = BlockPos(startX, startY + dy, startZ + dz)

                val frameEntity = frameMap[targetPos]

                if (frameEntity != null) {
                    val cached = CachedFrame(frameEntity, frameEntity.rotation)
                    persistentFrames[index] = cached
                    result.add(cached)
                } else {
                    persistentFrames.remove(index)
                    result.add(null)
                }
            }
        }
        return result
    }
}