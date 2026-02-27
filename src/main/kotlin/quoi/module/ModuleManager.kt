package quoi.module

import quoi.api.events.KeyEvent
import quoi.api.events.MouseEvent
import quoi.api.events.core.EventBus
import quoi.api.input.CatKeys
import quoi.module.impl.dungeon.*
import quoi.module.impl.mining.CrystalHollowsMap
import quoi.module.impl.mining.CrystalHollowsScanner
import quoi.module.impl.mining.GrieferTracker
import quoi.module.impl.misc.*
import quoi.module.impl.player.AutoSprint
import quoi.module.impl.player.PlayerDisplay
import quoi.module.impl.player.Tweaks
import quoi.module.impl.render.*
import quoi.module.settings.impl.KeybindSetting

object ModuleManager {
    val modules = mutableListOf<Module>()

    fun initialise() {
        modules += listOf(
            // DUNGEONS
            ShadowAssassinAlert,
            LeapMenu,
            AutoAlign,
            LeverAura,
            AutoCloseChest,
            FullBlockHitboxes,
            GhostBlocks,
            DungeonAbilities,
            BossESP,
            InventoryWalk,
            CancelInteract,
            SecretTriggerBot,
            InvincibilityTimer,
            AutoSS,
            DungeonBreaker,
            NecronPlatformHighlight,
//            DungeonMap,
            TerminalAura,
            AutoMask,
            BarrierBoom,
            TickTimers,
            DungeonESP,
            FuckDiorite,
            AutoGFS,
            Splits,
            Secrets,
//            AutoBloodRush,
            AutoRoutes,
            SecretAura,

            // MISC
            Test,
            Chat,
            ChatReplacements, // todo remove/replace
            AutoDialogue,
            PetKeybinds,
            Titles,
            WardrobeKeybinds,
            AntiNick,
            AutoClicker,
            Inventory,
            ItemAnimations,
//            CustomTriggers,

            // PLAYER
            AutoSprint,
            PlayerDisplay,
            Tweaks,

            // RENDER
            NameTags,
            RenderOptimiser,
            NickHider,
            ClickGui,
            PlayerESP,
            EtherwarpOverlay,

            // MINING
            CrystalHollowsMap,
            CrystalHollowsScanner,
            GrieferTracker,
        )

        modules.forEach { module ->
            module.keybinding.let {
                module.register(KeybindSetting("Key bind", it, desc = "Toggles the module"))
            }
        }

//        EventBus.on<AreaEvent.Main> { modules.forEach { it.onToggle(it.enabled) } }
//        EventBus.on<AreaEvent.Sub> { modules.forEach { it.onToggle(it.enabled) } }

        EventBus.on<KeyEvent.Press> { invokeKeybind(key) }
        EventBus.on<MouseEvent.Click> { if (state) invokeKeybind(button - 100) }
    }

    private fun invokeKeybind(key: Int) {
        if (key == CatKeys.KEY_NONE) return

        modules.forEach { module ->
            module.settings.filterIsInstance<KeybindSetting>()
                .filter { it.value.key == key && it.value.isModifierDown() }
                .forEach { it.value.onPress?.invoke() }
        }
    }

    fun getModuleByName(name: String?): Module? = modules.firstOrNull { it.name.equals(name, true) }
}