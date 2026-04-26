package quoi.api.autoroutes2

import net.minecraft.client.player.LocalPlayer
import quoi.config.TypeNamed

abstract class RouteAwait : TypeNamed {

    abstract fun check(player: LocalPlayer, node: RouteNode): Boolean

    open fun reset() { }

    open fun onSecret() { }
}