package quoi.utils

import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.annotations.Init
import kotlin.math.sqrt

@Init
object EntityUtils {
    val entities get() = getEntities()
    val playerEntities get() = getPlayerEntities()
    val playerEntitiesNoSelf get() = getPlayerEntities(true)

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

    fun Entity.distanceTo(x: Double, y: Double, z: Double) = sqrt(distanceToSqr(x, y, z))

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

    @JvmName("getAllEntities")
    fun getEntities() =
        mc.level?.entitiesForRendering()?.asSequence().orEmpty()

    @JvmName("getAllEntitiesByClass")
    inline fun <reified E : Entity> getEntities() =
        getEntities().filterIsInstance<E>()

    @JvmName("getPlayerEntities_")
    private fun getPlayerEntities(noSelf: Boolean = false): List<Player> {
        val players = mc.level?.players() ?: return emptyList()
        val result = ArrayList<Player>(players.size)

        for (player in players) {
            if (player.uuid.version() != 4) continue
            if (noSelf && player == mc.player) continue
            result.add(player)
        }

        return result
    }

    fun getEntity(id: Int): Entity? = mc.level?.getEntity(id)

    inline fun <reified E : Entity> getEntities(radius: Double, noinline predicate: (E) -> Boolean = { true }) =
        mc.player?.let { getEntities<E>(it.position(), radius, predicate) }.orEmpty()

    inline fun <reified E : Entity> getEntities(vec: Vec3, radius: Double, noinline predicate: (E) -> Boolean = { true }) =
        getEntities<E>(vec.aabb(radius), predicate)

    inline fun <reified E : Entity> getEntities(aabb: AABB, noinline predicate: (E) -> Boolean = { true }) =
        mc.level?.getEntitiesOfClass<E>(E::class.java, aabb, predicate).orEmpty()
}