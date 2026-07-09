package quoi.module.impl.misc.dojo.impl

import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import quoi.api.abobaui.dsl.ms
import quoi.api.animations.Animation
import quoi.api.events.BlockEvent
import quoi.api.events.core.on
import quoi.module.impl.misc.dojo.Dojo
import quoi.module.impl.misc.dojo.Dojo.centrePos
import quoi.module.impl.misc.dojo.DojoType
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.addVec
import quoi.utils.getDirection
import quoi.utils.skyblock.player.MovementUtils.cancelMovementTask
import quoi.utils.skyblock.player.MovementUtils.fullStop
import quoi.utils.skyblock.player.MovementUtils.moveTo
import quoi.utils.skyblock.player.RotationUtils.rotateSmoothly

// parkour shit

// todo fix it randomly stopping
object Swiftness : ToggleableGroup(Dojo, "Swiftness", subarea = "dojo arena") { // kinda rng but good enough for 2-3k
//    private val tracer = tracer(distance = null) // I doubt it's even useful since it goes crazy after around 1k score..
//    private val auto by switch("Auto")
    private val fullStop by switch("Full stop", desc = "Fully stops after walking.") // doesn't really change anything lowkey
    private val magicNumber by slider("Magic number", 0.3, 0.0, 0.5, 0.05, desc = "Adjust it and maybe it will suck less.")
    private val magicNumber2 by slider("Magic number 2", 9, 4, 9, 0.5, desc = "Adjust it and maybe it will suck less 2.") // todo maybe get rid of these and fucking improve moveto.
    private val speed by slider("Rotation speed", 150, 0, 300, 50, desc = "Lower = better but less legit looking idk", unit = "ms")
    private val style by selector("Style", Animation.Style.Linear, desc = "Rotation style")

    private val blocks = ArrayDeque<Vec3>()
    private var moving = false

    init {
        on<BlockEvent.Update> {
            if (updated.block != Blocks.LIME_WOOL) return@on

            if (pos == centrePos) return@on

            val target = pos.center.addVec(y = 0.5)

            if (blocks.contains(target)) return@on

            val wasEmpty = blocks.isEmpty()
            blocks.add(target)

            if (wasEmpty && !moving) {
                move()
            }
        }
    }

    override fun onDisable() {
        blocks.clear()
        moving = false
        cancelMovementTask()
    }

    private fun move() {
        if (blocks.isEmpty()) {
            moving = false
            return
        }

        if (!player.onGround()) {
            moving = true
            scheduleTask(1) { move() }
            return
        }

        moving = true

        val pos = blocks.removeFirst()
        val dir = pos.subtract(player.position()).normalize()
        val to = pos.add(dir.scale(magicNumber))

        if (player.blockPosition().center.distanceToSqr(pos) > magicNumber2) {
            player.rotateSmoothly(getDirection(pos).withPitch(90), speed.ms, style = style.selected)
        }

        player.moveTo(to) {
            if (fullStop) player.fullStop()
            move()
        }
    }

    override val running: Boolean
        get() = super.running && Dojo.dojoType == DojoType.SWIFTNESS
}