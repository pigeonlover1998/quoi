package quoi.module.impl.dungeon.secrets.impl

import quoi.api.skyblock.dungeon.Dungeon
import quoi.module.impl.dungeon.secrets.Secrets
import quoi.module.settings.group.ToggleableGroup

// Kyleen
object FullBlock : ToggleableGroup(
    Secrets,
    name = "Full Block",
    desc = "Increases the hitboxes of buttons, chests, levers, and skulls to be 1x1 blocks."
) {
    @JvmStatic val shouldExpandHitboxes: Boolean get() = enabled && Dungeon.inDungeons
}