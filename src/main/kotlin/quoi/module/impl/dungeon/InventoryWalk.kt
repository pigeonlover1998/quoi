package quoi.module.impl.dungeon

import quoi.api.events.GuiEvent
import quoi.api.events.TickEvent
import quoi.module.Module
import quoi.module.settings.impl.NumberSetting
import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.screens.ChatScreen
import org.lwjgl.glfw.GLFW
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen

// Kyleen
object InventoryWalk : Module(
    "Inventory Walk",
    desc = "Allows movement in containers."
) {

    private val clickDelay = NumberSetting("Click Delay", 6.0, 3.0, 12.0, 1.0)
    private var delay = 0

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
        addSettings(clickDelay)

        on<TickEvent.Start> {
            if (mc.screen is ChatScreen || mc.screen is AbstractSignEditScreen) return@on

            if (AutoMask.isSwapping) {
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
            delay = clickDelay.value.toInt()
        }
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