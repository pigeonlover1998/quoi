package quoi.module.impl.misc.dojo.impl

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import quoi.api.abobaui.dsl.ms
import quoi.api.colour.Colour
import quoi.api.events.BlockEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.module.impl.misc.dojo.Dojo
import quoi.module.impl.misc.dojo.DojoType
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.distanceToSqr
import quoi.utils.getArrowDirection
import quoi.utils.skyblock.player.RotationUtils.rotateSmoothly
import quoi.utils.skyblock.player.SwapManager

// bow shit
object Mastery : ToggleableGroup(Dojo, "Mastery", subarea = "dojo arena") {

    private val tracer = tracer(colour = null, distance = null)
    private val amount by slider("Amount", 3, 1, 4).childOf(tracer.component)

    private val auto by switch("Auto")
    private val sneakToDisable by switch("Sneak to disable").childOf(::auto)
    private val offset by slider("Offset", 500.0, 0.0, 1000.0, 50.0, unit = "ms").childOf(::auto)

    private val blocks = mutableMapOf<BlockPos, Long>()
    private val colours = listOf(Colour.RED, Colour.ORANGE, Colour.YELLOW, Colour.GREEN)

    private var state = BowState.IDLE
    private var currentTarget: BlockPos? = null

    init {
        on<BlockEvent.Update> {
            if (updated.block == Blocks.LIME_WOOL) {
                if (player.blockPosition().distanceToSqr(pos) > 400) return@on
                blocks[pos] = System.currentTimeMillis() + 6_500
            }

            if (old.block == Blocks.RED_WOOL) {
                blocks.remove(pos)
            }
        }

        on<RenderEvent.World> {
            if (!tracer.enabled || blocks.isEmpty()) return@on

            blocks.entries.sortedBy { it.value }.take(amount).forEachIndexed { i, (pos, _) ->
                tracer.draw(ctx, pos.center, colours[i])
            }
        }

        on<TickEvent.End> { // maybe use ticker
            val currentTime = System.currentTimeMillis()
            blocks.entries.removeIf { it.value < currentTime }
            if (!auto) return@on
            if (player.isCrouching && sneakToDisable) {
                if (state == BowState.DRAWING) mc.options.keyUse.isDown = false
                state = BowState.IDLE
                return@on
            }

            when (state) {
                BowState.IDLE -> {
                    val closest = blocks.entries.minByOrNull { it.value } ?: return@on
                    currentTarget = closest.key

                    if (player.inventory.selectedSlot != 0) {
                        SwapManager.swapToSlot(0)
                        return@on
                    }

                    player.rotateSmoothly(getArrowDirection(currentTarget!!.center), 150.ms)

                    mc.options.keyUse.isDown = true
                    state = BowState.DRAWING
                }

                BowState.DRAWING -> {
                    val target = currentTarget

                    if (target == null || !blocks.containsKey(target)) {
                        mc.options.keyUse.isDown = false
                        state = BowState.IDLE
                        currentTarget = null
                        return@on
                    }

                    val expires = blocks[target]!!
                    val remaining = expires - currentTime

                    if (remaining < offset) {
                        mc.options.keyUse.isDown = false
                        state = BowState.RELEASED
                        blocks.remove(target)
                        currentTarget = null
                    }
                }

                BowState.RELEASED -> state = BowState.IDLE
            }
        }
    }

    override fun onDisable() {
        blocks.clear()
        state = BowState.IDLE
        currentTarget = null
        mc.options.keyUse.isDown = false
    }

    private enum class BowState { IDLE, DRAWING, RELEASED }

    override val running: Boolean
        get() = super.running && Dojo.dojoType == DojoType.MASTERY
}