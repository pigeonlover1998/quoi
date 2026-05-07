package quoi.api.autoroutes2.nodes

import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.phys.AABB
import quoi.QuoiMod.mc
import quoi.api.autoroutes2.RouteNode
import quoi.api.colour.Colour
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.vec.MutableVec3
import quoi.config.TypeName
import quoi.utils.equalsOneOf
import quoi.utils.skyblock.player.PacketOrderManager
import quoi.utils.skyblock.player.SwapManager

@TypeName("hype")
class HypeNode : RouteNode() {
    var yaw = 0f
    var pitch = 90f

    @Transient
    private var realYaw = 0f

    override val colour: Colour
        get() = Colour.PURPLE

    override val priority: Int
        get() = 16

    override fun update(room: OdonRoom) {
        super.update(room)
        realYaw = room.getRealYaw(yaw)
    }

    override fun execute(player: LocalPlayer, pos: MutableVec3): Boolean {
        if (!SwapManager.reserveSwapById("NECRON_BLADE", "SCYLLA", "HYPERION", "VALKYRIE", "ASTRAEA", "SPIRIT_SCEPTRE")) {
            return false
        }

        if (!hasBatNear(player)) return false

        val isDesynced = SwapManager.isDesynced()

        PacketOrderManager.register(PacketOrderManager.State.ITEM_USE) {
            val level = mc.level ?: return@register
            val gameMode = mc.gameMode as? quoi.mixins.accessors.MultiPlayerGameModeAccessor ?: return@register
            
            if (isDesynced) {
                gameMode.invokeEnsureHasSentCarriedItem()
            }
            
            if (!SwapManager.checkServerItem("NECRON_BLADE", "SCYLLA", "HYPERION", "VALKYRIE", "ASTRAEA", "SPIRIT_SCEPTRE")) {
                return@register
            }

            gameMode.invokeStartPrediction(level) { sequence ->
                net.minecraft.network.protocol.game.ServerboundUseItemPacket(
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    sequence,
                    realYaw,
                    pitch
                )
            }
        }

        return false
    }

    private fun hasBatNear(player: LocalPlayer): Boolean {
        val level = mc.level ?: return false
        val playerPos = player.position()
        val aabb = AABB(playerPos, playerPos).inflate(10.0)
        return level.getEntitiesOfClass(Bat::class.java, aabb).any { bat ->
            bat.distanceToSqr(playerPos) < 100 && bat.maxHealth.equalsOneOf(100f, 200f, 400f, 800f)
        }
    }

    override fun create(player: LocalPlayer, room: OdonRoom): RouteNode? {
        yaw = room.getRelativeYaw(player.yRot)
        pitch = 90f
        return this
    }
}
