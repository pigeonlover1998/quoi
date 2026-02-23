package quoi.module.impl.dungeon

import quoi.api.skyblock.dungeon.Dungeon.inDungeons
import quoi.module.Module

// Kyleen
object FullBlockHitboxes : Module("Full Block Hitboxes")  {
    @JvmStatic val shouldExpandHitboxes: Boolean get() = enabled && inDungeons
}