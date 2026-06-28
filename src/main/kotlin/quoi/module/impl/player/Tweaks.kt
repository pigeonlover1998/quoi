package quoi.module.impl.player

import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.api.skyblock.Location.inSkyblock
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf

object Tweaks : Module(
    name = "Tweaks",
    desc = "Various player tweaks."
) {
    @JvmStatic val fixDoubleSneak by switch("Fix double sneak", desc = "Fixes a bug where your camera can bounce when you quickly sneak and unsneak.") // kinda a rendering thing rite? :grin:
    @JvmStatic val instantSneak by switch("Instant sneak", desc = "Instantly moves your camera when sneaking.")

    private val skyblockOnly by text("Skyblock only", desc = "Hypixel skyblock only features")
    @JvmStatic val disableItemCooldowns by switch("Disable item cooldowns", desc = "Disables item cooldowns such as ender pearls.").childOf(::skyblockOnly)
    @JvmStatic val fixInteract by switch("Fix interaction", desc = "Fixes a bug where you can't interact when SA jumps the player.").childOf(::skyblockOnly)

    /**
     * from OdinFabric (BSD 3-Clause)
     * copyright (c) 2025-2026 odtheking
     * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/skyblock/NoCursorReset.kt
     */
    private val noCursorReset by switch("No cursor reset").childOf(::skyblockOnly)

    private var time = System.currentTimeMillis()
    private var wasNotNull = false

    init {
        on<TickEvent.End> {
            if (mc.screen != null) {
                wasNotNull = true
                time = System.currentTimeMillis()
            } else if (wasNotNull && mc.screen == null) {
                wasNotNull = false
                time = System.currentTimeMillis()

            }
        }
    }

    @JvmStatic
    fun should(condition: Boolean): Boolean = this.enabled && condition // idkman

    @JvmStatic
    fun shouldSb(condition: Boolean): Boolean = this.enabled && inSkyblock && condition

    @JvmStatic
    fun shouldHookMouse(): Boolean =
        System.currentTimeMillis() - time < 150 && shouldSb(noCursorReset)
}