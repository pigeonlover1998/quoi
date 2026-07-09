package quoi.module.impl.misc.dojo.impl

import net.minecraft.world.entity.monster.skeleton.WitherSkeleton
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import quoi.api.abobaui.dsl.ms
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.module.impl.misc.dojo.Dojo
import quoi.module.impl.misc.dojo.Dojo.centre
import quoi.module.impl.misc.dojo.DojoType
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.EntityUtils.getEntities
import quoi.utils.EntityUtils.helmet
import quoi.utils.EntityUtils.interpolatedBox
import quoi.utils.component1
import quoi.utils.component2
import quoi.utils.component3
import quoi.utils.getDirection
import quoi.utils.skyblock.player.MovementUtils.moveTo
import quoi.utils.skyblock.player.MovementUtils.moving
import quoi.utils.skyblock.player.PlayerUtils.at
import quoi.utils.skyblock.player.RotationUtils.rotateSmoothly

// skeleton shit
object Control : ToggleableGroup(Dojo, "Control", subarea = "dojo arena") {
    private val highlight by switch("Highlight correct", /*desc = "Highlights mobs based on held weapon."*/)
    private val style = highlight(glow = false).childOf(::highlight)

    private val aim by switch("Auto aim")
    private val ticks by slider("Prediction ticks", 5, 1, 10, 1, unit = "t").childOf(::aim)
    private val keep by switch("Keep in centre")

    private var lastRot = 0L
    private var position: Vec3? = null
    private var wither: WitherSkeleton? = null

    init {
        on<TickEvent.End> {
            if (keep && !moving() && !player.at(centre)) {
                player.moveTo(centre)
            }

            val skeleton = getEntities<WitherSkeleton>(centre, radius = 25.0) { it.helmet.item != Items.REDSTONE_BLOCK } // todo use entity spawn event
                .minByOrNull { it.distanceToSqr(player.position()) } ?: return@on
            if (skeleton.position() == skeleton.oldPosition()) return@on // if server lags
            val (x, y, z) = skeleton.position()
            val (ox, oy, oz) = skeleton.oldPosition()

            val px = x + (x - ox) * ticks
            val py = y + (y - oy)
            val pz = z + (z - oz) * ticks
            position = Vec3(px, py, pz)
            wither = skeleton


            if (aim) { // suboptimal
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastRot <= 40) return@on
                lastRot = currentTime

                val dir = getDirection(Vec3(px, py + 1.5, pz))
                player.rotateSmoothly(dir, 150.ms)
            }
        }

        on<RenderEvent.World> { // maybe should render all skeletons since legits won't know which one is closest but idgaf
            if (!highlight || wither == null || position == null) return@on
            val box = wither!!.interpolatedBox.move(position!!)
            style.draw(ctx, box)
        }
    }

    override fun onDisable() {
        position = null
        wither = null
    }

    override val running: Boolean
        get() = super.running && Dojo.dojoType == DojoType.CONTROL
}