package quoi.module.impl.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon.inP3
import quoi.api.skyblock.dungeon.Dungeon.isDead
import quoi.api.skyblock.invoke
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.ChatUtils.literal
import quoi.utils.EntityUtils
import quoi.utils.addVec
import quoi.utils.render.drawText

// Kyleen
object ArrowAlign : Module(
    "Arrow Align",
    desc = "Shows the solution for arrow align device.",
    area = Island.Dungeon(7, inBoss = true)
) {
    private val solver by switch("Solver")
    private val auto by switch("Auto")
    private val range by slider("Range", 5.0, 2.1, 6.5, 0.1, desc = "Maximum range for the align aura.").childOf(::auto)

    private val deviceStandLocation = BlockPos(0, 120, 77)
    private val deviceCorner = BlockPos(-2, 120, 75)

    private val recentClicks = LongArray(25)
    private val persistentFrames = arrayOfNulls<CachedFrame>(25)
    private val renderList = mutableListOf<Pair<Vec3, Int>>()

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
            if (!isDead && (solver || auto)) handleArrowAlign()
        }

        on<RenderEvent.World> {
            if (!solver || renderList.isEmpty()) return@on
            renderList.forEach { (pos, clicks) ->
                val col =
                    if (clicks < 3) Colour.GREEN
                    else if (clicks < 5) Colour.YELLOW
                    else Colour.RED

                ctx.drawText(literal(clicks.toString()).withColor(col.rgb), pos, scale = 1.0f, depth = true)
            }
        }

        on<WorldEvent.Change> {
            persistentFrames.fill(null)
            recentClicks.fill(0L)
            renderList.clear()
        }
    }

    private fun handleArrowAlign() {
        if (player.distanceToSqr(Vec3.atCenterOf(deviceStandLocation)) > 100) {
            renderList.clear()
            return
        }

        val currentFrames = getCurrentFrames()

        var solution: List<Int?>? = null

        for (sol in solutions) {
            var match = true
            for (i in 0 until 25) {
                if ((sol[i] == null) != (currentFrames[i] == null)) {
                    match = false
                    break
                }
            }
            if (match) {
                solution = sol
                break
            }
        }
        if (solution == null) return

        renderList.clear()
        var frames = 0
        for (i in 0 until 25) {
            val frame = currentFrames[i] ?: continue
            val target = solution[i] ?: continue
            val needed = (target - frame.rotation + 8) % 8
            if (needed > 0) {
                renderList.add(frame.entity.position().addVec(x = 0.1) to needed)
                frames++
            }
        }

        if (!auto) return

        val closest = (0 until 25).minByOrNull { i ->
            val f = currentFrames[i] ?: return@minByOrNull Double.MAX_VALUE
            val t = solution[i] ?: return@minByOrNull Double.MAX_VALUE
            if ((t - f.rotation + 8) % 8 <= 0) Double.MAX_VALUE else player.distanceToSqr(f.entity)
        }

        for (i in 0 until 25) {
            val frame = currentFrames[i] ?: continue
            val targetRotation = solution[i] ?: continue
            var clicksNeeded = (targetRotation - frame.rotation + 8) % 8

            if (clicksNeeded <= 0) continue
            if (frame.entity.distanceToSqr(player) > range * range) continue

            if (!inP3 && i == closest) {
                clicksNeeded--
            }

            if (clicksNeeded > 0) {
                recentClicks[i] = System.currentTimeMillis()
                repeat(clicksNeeded) {
                    frame.rotation = (frame.rotation + 1) % 8
                    mc.connection?.send(
                        ServerboundInteractPacket.createInteractionPacket(
                            frame.entity,
                            player.isShiftKeyDown,
                            InteractionHand.MAIN_HAND,
                            Vec3(0.03125, 0.0, 0.0)
                        )
                    )

                    mc.connection?.send(
                        ServerboundInteractPacket.createInteractionPacket(
                            frame.entity,
                            player.isShiftKeyDown,
                            InteractionHand.MAIN_HAND
                        )
                    )
                }
                break
            }
        }
    }

    private fun getCurrentFrames(): Array<CachedFrame?> {
        val scanBox = AABB(deviceCorner).expandTowards(5.0, 5.0, 5.0).inflate(1.0)
        val entities = EntityUtils.getEntities<ItemFrame>(scanBox) {
            it.item.item == Items.ARROW
        }

        val frames = arrayOfNulls<CachedFrame>(25)
        val start = deviceCorner
        val currentTime = System.currentTimeMillis()

        for (dz in 0 until 5) {
            for (dy in 0 until 5) {
                val index = dy + dz * 5

                if (persistentFrames[index] != null && currentTime - recentClicks[index] < 1000) {
                    frames[index] = persistentFrames[index]
                    continue
                }

                val targetPos = BlockPos(start.x, start.y + dy, start.z + dz)

                val frameEntity = entities.find { it.blockPosition() == targetPos }

                if (frameEntity != null) {
                    val cached = CachedFrame(frameEntity, frameEntity.rotation)
                    persistentFrames[index] = cached
                    frames[index] = cached
                } else {
                    persistentFrames[index] = null
                }
            }
        }
        return frames
    }
}