package quoi.module.impl.player

import quoi.module.Module
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.DropdownSetting
import quoi.api.skyblock.Location.inSkyblock
import quoi.api.skyblock.Location.onModernIsland

object Tweaks : Module(
    name = "Tweaks",
    desc = "Various player tweaks."
) {
    @JvmStatic val fixDoubleSneak by BooleanSetting("Fix double sneak", desc = "Fixes a bug where your camera can bounce when you quickly sneak and unsneak.") // kinda a rendering thing rite? :grin:
    @JvmStatic val instantSneak by BooleanSetting("Instant sneak", desc = "Instantly moves your camera when sneaking.")

    private val skyblockOnly by DropdownSetting("Skyblock only", desc = "Hypixel skyblock only features").collapsible()
    @JvmStatic val legacySneakHeight by BooleanSetting("Legacy sneak height", desc = "Reverts sneak height to pre 1.13 height.").withDependency(skyblockOnly)
    @JvmStatic val sneakLagFix by BooleanSetting("Sneak lag fix", desc = "idkman sometimes it works sometimes it doesn't.").withDependency(skyblockOnly)
    @JvmStatic val disableSwimming by BooleanSetting("Disable swimming", desc = "Disables swimming animation.").withDependency(skyblockOnly)
    @JvmStatic val disableItemCooldowns by BooleanSetting("Disable item cooldowns", desc = "Disables item cooldowns such as ender pearls.").withDependency(skyblockOnly)
    @JvmStatic val fixInteract by BooleanSetting("Fix interaction", desc = "Fixes a bug where you can't interact when SA jumps the player.").withDependency(skyblockOnly) // todo move to no interact module

    @JvmStatic
    fun should(condition: Boolean): Boolean = this.enabled && condition // idkman

    @JvmStatic
    fun shouldSb(condition: Boolean): Boolean = this.enabled && inSkyblock && !onModernIsland && condition
}