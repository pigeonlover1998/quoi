package quoi.module.impl.dungeon

import quoi.api.events.PacketEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.module.Module
import quoi.utils.skyblock.player.PlayerUtils
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket

object ShadowAssassinAlert : Module(
    "Shadow Assassin Alert",
    area = Island.Dungeon,
    desc = "Sends an alert when SA jumps you."
) {
    init {
        on<PacketEvent.Received, ClientboundInitializeBorderPacket> {
            if (((Dungeon.isFloor(3) || Dungeon.isFloor(2)) && Dungeon.inBoss)) return@on
            PlayerUtils.setTitle("", "§aShadow Assassin!", playSound = true, stayAlive = 35, fadeOut = 0)
        }
    }
}