package quoi.api.autoroutes2.awaits

import net.minecraft.client.player.LocalPlayer
import quoi.api.autoroutes2.RouteAwait
import quoi.api.autoroutes2.RouteNode
import quoi.api.autoroutes2.nodes.EtherwarpNode
import quoi.config.TypeName
import quoi.utils.addVec
import quoi.utils.skyblock.player.PlayerUtils.getEyeHeight
import quoi.utils.skyblock.item.TeleportUtils.traverseVoxels

@TypeName("raycast")
class RaycastAwait : RouteAwait() {
    override fun check(player: LocalPlayer, node: RouteNode): Boolean {
        if (node !is EtherwarpNode) return true

        val from = node.pos.addVec(y = getEyeHeight(true).toDouble())
        val dir = node.realTarget.subtract(from).normalize()
        val to = node.realTarget.add(dir.scale(0.001))

        val ether = traverseVoxels(from, to, true)
        return ether.succeeded && ether.pos != null
    }
}