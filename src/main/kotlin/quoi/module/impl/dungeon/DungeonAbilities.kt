package quoi.module.impl.dungeon

import quoi.api.events.*
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon.currentDungeonPlayer
import quoi.api.skyblock.dungeon.Dungeon.isDead
import quoi.api.skyblock.dungeon.DungeonClass
import quoi.module.Module
import quoi.module.settings.impl.BooleanSetting
import quoi.utils.StringUtils.noControlCodes

// Kyleen
object DungeonAbilities : Module(
    "Dungeon Abilities",
    area = Island.Dungeon
) {
    private val autoWish by BooleanSetting("Healer auto wish")

    init {
        on<ChatEvent.Packet> {
            if (isDead) return@on
            when (message.noControlCodes) {
                "[BOSS] Goldor: You have done it, you destroyed the factory…" -> {
                    dropItem()
                }
                "⚠ Maxor is enraged! ⚠" -> {
                    dropItem()
                }
            }
        }
    }

    private fun dropItem() {
        if (!autoWish || currentDungeonPlayer.clazz != DungeonClass.Healer) return
        mc.player?.drop(false)
    }
}