package quoi.module.impl.dungeon.secrets

import quoi.api.skyblock.location.Island
import quoi.module.Module
import quoi.module.impl.dungeon.secrets.impl.AutoCloseChest
import quoi.module.impl.dungeon.secrets.impl.SecretAura
import quoi.module.impl.dungeon.secrets.impl.SecretHighlight
import quoi.module.impl.dungeon.secrets.impl.SecretTriggerBot

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
    }
}