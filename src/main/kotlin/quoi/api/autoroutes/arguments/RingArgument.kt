package quoi.api.autoroutes.arguments

import quoi.api.autoroutes.RouteRing
import quoi.config.TypeNamed

sealed interface RingArgument : TypeNamed {
    fun check(ring: RouteRing): Boolean
}