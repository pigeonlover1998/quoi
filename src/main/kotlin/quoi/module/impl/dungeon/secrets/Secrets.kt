package quoi.module.impl.dungeon.secrets

import quoi.api.skyblock.location.Island
import quoi.module.Module
import quoi.module.impl.dungeon.secrets.impl.*

@Suppress("unused_expression")
object Secrets : Module(
    "Secrets",
    desc = "Secrets stuff",
    area = Island.Dungeon
) {
    init {
        SecretHighlight
        SecretAura
        SecretTriggerBot
        AutoCloseChest
        FullBlock
    }
}