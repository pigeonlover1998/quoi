package quoi.api.autoroutes.nodes

import net.minecraft.client.player.LocalPlayer
import quoi.api.autoroutes.RouteNode
import quoi.api.colour.Colour
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.vec.MutableVec3
import quoi.api.world.Direction
import quoi.config.TypeName
import quoi.module.impl.dungeon.autoclear.impl.AutoRoutes
import quoi.utils.ChatUtils.modMessage
import quoi.utils.equalsOneOf
import quoi.utils.skyblock.item.ItemUtils.registryPath
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.RotationUtils.pitch
import quoi.utils.skyblock.player.RotationUtils.yaw
import quoi.utils.skyblock.player.SwapManager

@TypeName("use_item")
class UseItemNode : RouteNode() {
    var yaw = 0f
    var pitch = 0f
    var item = ""

    @Transient
    var realYaw = 0f

    override val colour: Colour
        get() = Colour.BROWN

    override val priority: Int
        get() = 50

    override fun update(room: OdonRoom) {
        super.update(room)
        realYaw = room.getRealYaw(yaw)
    }

    override fun execute(player: LocalPlayer, pos: MutableVec3): Boolean {
        val slot = (0..8).find {
            val stack = player.inventory.getItem(it)
            item.equalsOneOf(stack.skyblockId, stack.registryPath)
        }

        if (slot == null) {
            modMessage("&cItem &e$item&c not found!")
            return true
        }

        if (player.inventory.selectedSlot != slot) {
            return !SwapManager.swapToSlot(slot).success
        }

        AutoRoutes.queueInteract(Direction(realYaw, pitch))
        return true
    }

    override fun create(player: LocalPlayer, room: OdonRoom): RouteNode? {
        val stack = player.mainHandItem
        if (stack.isEmpty) return null

        yaw = room.getRelativeYaw(player.yaw)
        pitch = player.pitch
        item = stack.skyblockId ?: stack.registryPath
        return this
    }
}