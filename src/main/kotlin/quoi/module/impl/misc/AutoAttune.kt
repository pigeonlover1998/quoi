package quoi.module.impl.misc

import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import quoi.api.events.ChatEvent
import quoi.api.events.EntityEvent
import quoi.api.events.core.on
import quoi.module.Module
import quoi.utils.EntityUtils.getEntities
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.player.PlayerUtils.rightClick
import quoi.utils.skyblock.player.SwapManager

object AutoAttune : Module(
    "Auto Attune",
    subarea = "smoldering tomb"
) {
    private var lastSwap = -1L

    init {
        /**
         * this event is inconsistent/slow sometimes (I think?). idc enough since
         * this slayer is fucking shit and makes 20m/h with 8b setup...
         */
        on<EntityEvent.Attack> {
            if (System.currentTimeMillis() - lastSwap < 400L) return@on

            val attunement = getEntities<ArmorStand>(entity.position(), 3.0).firstNotNullOfOrNull { stand ->
                val name = stand.customName?.string?.noControlCodes ?: return@firstNotNullOfOrNull null
                Attunement.entries.firstOrNull { name.contains(it.name, true) }
            } ?: return@on

            val result = SwapManager.swapById(*attunement.daggers)

            if (!result.success) return@on

            if (player.mainHandItem.item != attunement.sword) {
                player.rightClick()
            }

            lastSwap = System.currentTimeMillis()
        }

        on<ChatEvent.Receive> {
            if (message.noControlCodes.startsWith("Strike using the")) cancel()
        }
    }

    private val aa = arrayOf("FIREDUST_DAGGER", "BURSTFIRE_DAGGER", "HEARTFIRE_DAGGER")
    private val sc = arrayOf("MAWDUST_DAGGER", "BURSTMAW_DAGGER", "HEARTMAW_DAGGER")

    private enum class Attunement(val sword: Item, val daggers: Array<String>) {
        ASHEN(Items.STONE_SWORD, aa),
        AURIC(Items.GOLDEN_SWORD, aa),
        SPIRIT(Items.IRON_SWORD, sc),
        CRYSTAL(Items.DIAMOND_SWORD, sc)
    }
}