package quoi.module

import quoi.api.events.KeyEvent
import quoi.api.events.MouseEvent
import quoi.api.events.core.EventBus
import quoi.api.input.CatKeys
import quoi.module.impl.dungeon.*
import quoi.module.impl.dungeon.puzzlesolvers.PuzzleSolvers
import quoi.module.impl.mining.*
import quoi.module.impl.misc.*
import quoi.module.impl.misc.riftsolvers.MirrorverseSolvers
import quoi.module.impl.player.*
import quoi.module.impl.render.*
import quoi.module.settings.impl.KeybindComponent

object ModuleManager {
    val modules = mutableListOf<Module>()

    fun initialise() {
        modules += listOf(
            // DUNGEONS
            ShadowAssassinAlert,
            LeapMenu,
            ArrowAlign,
            AutoLeap,
            AutoCloseChest,
            FullBlockHitboxes,
            CancelInteract,
            SecretTriggerBot,
            InvincibilityTimer,
            SimonSays,
            DungeonBreaker,
            NecronPlatformHighlight,
//            DungeonMap,
            TerminalAura,
            AutoMask,
            TickTimers,
            DungeonESP,
            FuckDiorite,
            AutoGFS,
            Splits,
            Secrets,
            AutoBloodRush,
            AutoRoutes,
            SecretAura,
            PuzzleSolvers,

            // MISC
            Test,
            Chat,
            ChatReplacements, // todo remove/replace
            AutoDialogue,
            PetKeybinds,
            WardrobeKeybinds,
            AntiNick,
            AutoClicker,
            Inventory,
//            CustomTriggers,
            MirrorverseSolvers,
            CatMode,

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
                module.register(KeybindComponent("Key bind", it, desc = "Toggles the module"))
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
            module.settings.filterIsInstance<KeybindComponent>()
                .filter { it.value.key == key && it.value.isModifierDown() }
                .forEach { it.value.onPress?.invoke() }
        }
    }

    fun getModuleByName(name: String?): Module? = modules.firstOrNull { it.name.equals(name, true) }
}