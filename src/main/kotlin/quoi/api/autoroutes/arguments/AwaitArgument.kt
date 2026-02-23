package quoi.api.autoroutes.arguments

import quoi.QuoiMod.mc
import quoi.api.autoroutes.RouteRing
import quoi.config.TypeName
import quoi.module.impl.dungeon.AutoRoutes
import quoi.utils.EntityUtils
import quoi.utils.equalsOneOf
import net.minecraft.world.entity.ambient.Bat

@TypeName("await_secret")
class AwaitArgument(val amount: Int? = null) : RingArgument {
    override fun check(ring: RouteRing): Boolean {
        if (ring in AutoRoutes.completedAwaits) return true

        AutoRoutes.registerAwait(ring)

        EntityUtils.entities.forEach { entity ->
            if (entity !is Bat || entity.id in AutoRoutes.batIds) return@forEach
            if (!entity.maxHealth.equalsOneOf(100f, 200f, 400f, 800f)) return@forEach
            if (entity.distanceTo(mc.player!!) <= 10) { // maybe 10 is too much idk
                AutoRoutes.batIds.add(entity.id)
                AutoRoutes.secretsAwaited++
            }
        }

        val goog = AutoRoutes.secretsAwaited >= (amount ?: 1)

        if (goog) {
            if (AutoRoutes.secretsAwaited < 999) { // don't addd skipped awaits
                AutoRoutes.completedAwaits.add(ring)
            }
        }

        return goog
    }
}