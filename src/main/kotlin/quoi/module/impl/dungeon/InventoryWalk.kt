package quoi.module.impl.dungeon

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import org.lwjgl.glfw.GLFW
import quoi.api.events.GuiEvent
import quoi.api.events.TickEvent
import quoi.module.Module

// Kyleen
object InventoryWalk : Module(
    "Inventory Walk",
    desc = "Allows movement in containers."
) {

    private val clickDelay by slider("Click delay", 6, 3, 12, unit = "t")
    private val blacklist by switch("Blacklist", desc = "Stops movement in sell guis + terminals.")

    private var delay = 0
    private val blacklistedTitles = listOf("Trades", "Booster Cookie", "Farm Merchant", "Ophelia", "Correct all the panes!", "Change all to same color!", "Click in order!", "What starts with:", "Select all the", "Click the button on time!")

    private val movementKeys: List<KeyMapping>
        get() = listOf(
            mc.options.keyUp,
            mc.options.keyDown,
            mc.options.keyLeft,
            mc.options.keyRight,
            mc.options.keyJump,
            mc.options.keySprint
        )

    init {

        on<TickEvent.Start> {
            val screen = mc.screen ?: return@on

            if (isTyping(screen) || blacklist && isBlacklisted(screen)) {
                movementKeys.forEach { it.isDown = false }
                return@on
            }

            if (delay > 0) {
                delay--
                movementKeys.forEach { it.isDown = false }
                return@on
            }

            if (delay == 0) {
                handleMovement()
            } else {
                movementKeys.forEach { it.isDown = false }
            }
        }

        on<GuiEvent.Slot.Click> {
            mc.screen ?: return@on
            movementKeys.forEach { it.isDown = false }
            delay = clickDelay
        }
    }

    private fun isTyping(screen: Screen): Boolean {
        return screen is ChatScreen || screen is AbstractSignEditScreen || screen.children().any { it is EditBox && it.isFocused }
    }

    private fun isBlacklisted(screen: Screen): Boolean {
        val title = screen.title.string
        return blacklistedTitles.any { title.contains(it) }
    }

    private fun handleMovement() {
        val window = mc.window ?: return
        val windowHandle = window.handle()

        movementKeys.forEach { keyBinding ->
            val boundKey = KeyBindingHelper.getBoundKeyOf(keyBinding)
            val keyCode = boundKey.value

            val isPressed = if (boundKey.type == InputConstants.Type.MOUSE) {
                GLFW.glfwGetMouseButton(windowHandle, keyCode) == GLFW.GLFW_PRESS
            } else {
                InputConstants.isKeyDown(window, keyCode)
            }

            keyBinding.isDown = isPressed
        }
    }
}