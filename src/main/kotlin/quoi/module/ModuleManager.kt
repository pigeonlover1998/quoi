package quoi.module

import quoi.api.events.GuiEvent
import quoi.api.events.KeyEvent
import quoi.api.events.MouseEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.on
import quoi.api.input.CatKeys
import quoi.module.impl.dungeon.*
import quoi.module.impl.dungeon.autoclear.impl.*
import quoi.module.impl.dungeon.floor7.ArrowAlign
import quoi.module.impl.dungeon.floor7.FuckDiorite
import quoi.module.impl.dungeon.floor7.NecronPlatformHighlight
import quoi.module.impl.dungeon.floor7.SimonSays
import quoi.module.impl.dungeon.floor7.TerminalAura
import quoi.module.impl.dungeon.puzzlesolvers.PuzzleSolvers
import quoi.module.impl.mining.*
import quoi.module.impl.misc.*
import quoi.module.impl.misc.catmode.CatMode
import quoi.module.impl.misc.chat.Chat
import quoi.module.impl.misc.inventory.Inventory
import quoi.module.impl.misc.riftsolvers.MirrorverseSolvers
import quoi.module.impl.player.*
import quoi.module.impl.render.*
import quoi.module.impl.render.clickgui.ClickGui
import quoi.module.settings.impl.KeybindComponent

object ModuleManager : EventListener {
    val modules = mutableListOf<Module>()

    fun initialise() {
        modules += listOf(
            ClickGui,
            // DUNGEONS
            ShadowAssassinAlert,
            LeapMenu,
            ArrowAlign,
            AutoLeap,
            AutoCloseChest,
            FullBlockHitboxes,
            SecretTriggerBot,
            InvincibilityTimer,
            SimonSays,
            DungeonBreaker,
            NecronPlatformHighlight,
//            DungeonMap,
            TerminalAura,
            TickTimers,
            DungeonESP,
            FuckDiorite,
            AutoGFS,
            Splits,
            Secrets,
            SecretAura,
            PuzzleSolvers,
            InteractiveMap,
            DungeonMap,
            AutoRoutes,
            BloodCamp,
            WarpCooldown,

            // MISC
            Test,
            Chat,
            ChatReplacements, // todo remove/replace
            PetKeybinds,
            WardrobeKeybinds,
            AntiNick,
            AutoClicker,
            Inventory,
//            CustomTriggers,
            MirrorverseSolvers,
            CatMode,
            AutoCarnival,

            // PLAYER
            AutoSprint,
            PlayerDisplay,
            Tweaks,
            RemoteControl,

            // RENDER
            NameTags,
            RenderOptimiser,
            NickHider,

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

        on<KeyEvent.Press> { invokeKeybind(key, true) }
        on<KeyEvent.Release> { invokeKeybind(key, false) }
        on<MouseEvent.Click> { invokeKeybind(button - 100, state) }

        on<GuiEvent.Key.Press> { invokeKeybind(key, true) }
        on<GuiEvent.Key.Release> { invokeKeybind(key, false) }
        on<GuiEvent.Click> { invokeKeybind(button - 100, state) }
    }

    private fun invokeKeybind(key: Int, pressed: Boolean) {
        if (key == CatKeys.KEY_NONE) return

        modules.forEach { module ->
            module.settings.filterIsInstance<KeybindComponent>()
                .filter { it.value.key == key && it.value.isModifierDown() }
                .forEach { component ->
                    if (pressed) component.value.onPress?.invoke()
                    else component.value.onRelease?.invoke()
                }
        }
    }

    fun getModuleByName(name: String?): Module? = modules.firstOrNull { it.name.equals(name, true) }
}