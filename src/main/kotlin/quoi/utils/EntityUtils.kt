package quoi.utils

import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.api.events.TickEvent
import quoi.api.events.core.EventBus
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

object EntityUtils {
    val entities get() = entitiesToRender.toList()
    val playerEntities get() = entitiesToRender.filter { it is Player && it.uuid.version() != 2 }
    val playerEntitiesNoSelf get() = entitiesToRender.filter { it is Player && it != mc.player && it.uuid.version() != 2 }

    private val entitiesToRender = mutableListOf<Entity>()

    fun init() {
        EventBus.on<TickEvent.End> {
            entitiesToRender.clear()
            mc.level?.entitiesForRendering()?.forEach { entitiesToRender += it }
        }
    }

    inline val Entity.renderX: Double
        get() = xo + (x - xo) * mc.deltaTracker.getGameTimeDeltaPartialTick(true)

    inline val Entity.renderY: Double
        get() = yo + (y - yo) * mc.deltaTracker.getGameTimeDeltaPartialTick(true)

    inline val Entity.renderZ: Double
        get() = zo + (z - zo) * mc.deltaTracker.getGameTimeDeltaPartialTick(true)

    inline val Entity.renderPos: Vec3
        get() = Vec3(renderX, renderY, renderZ)

    inline val Entity.interpolatedBox: AABB
        get() = boundingBox.move(renderX - x, renderY - y, renderZ - z)


    val Entity.distanceToCamera: Double get() {
        val cameraPos = mc.gameRenderer.mainCamera.position
        val dx = cameraPos.x - this.x
        val dy = cameraPos.y - this.y + this.getEyeHeight(this.pose)
        val dz = cameraPos.z - this.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun Entity.distanceTo(x: Double, y: Double, z: Double) = sqrt((this.x - x).let { dx ->
        val dy = this.y - y
        val dz = this.z - z
        dx * dx + dy * dy + dz * dz
    })

    fun Entity.distanceTo(vec: Vec3) = distanceTo(vec.x, vec.y, vec.z)

    fun Entity.distanceTo(pos: BlockPos) = distanceTo(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())

    // https://github.com/MeteorDevelopment/meteor-client/blob/6409c29693a8df6428aa8044212fe02f47e3a02f/src/main/java/meteordevelopment/meteorclient/utils/entity/EntityUtils.java#L186
    val Entity.colourFromDistance: Colour
        get() {
        val percent = this.distanceToCamera / 60.0

        if (percent !in 0.0..1.0) {
            return Colour.RGB(255, 0, 0)
        }

        val (r, g) = if (percent < 0.5) {
            val r = (255 - (255 * (percent - 0.5) / 0.5)).toInt()
            r to 255
        } else {
            val g = (255 * percent / 0.5).toInt()
            255 to g
        }

        return Colour.RGB(r, g, 0)
    }
}