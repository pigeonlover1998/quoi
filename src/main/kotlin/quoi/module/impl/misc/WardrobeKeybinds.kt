package quoi.module.impl.misc

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import quoi.api.events.GuiEvent
import quoi.api.input.CatKeys
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.impl.KeybindComponent
import quoi.utils.ChatUtils.modMessage
import quoi.utils.skyblock.ItemUtils.loreString
import quoi.utils.skyblock.player.ContainerUtils.clickSlot

/**
 * modified OdinLegacy (BSD 3-Clause)
 * copyright (c) 2023-2026 odtheking
 * original: https://github.com/odtheking/OdinLegacy/blob/main/src/main/kotlin/me/odinmain/features/impl/skyblock/WardrobeKeybinds.kt
 */
object WardrobeKeybinds : Module(
    "Wardrobe Keybinds",
    desc = "Keybinds for wardrobe."
) {
    private val unequipKeybind by keybind("Unequip", desc = "Unequips the current slot.")
    private val nextPageKeybind by keybind("Next page", desc = "Goes to the next page.")
    private val previousPageKeybind by keybind("Previous page", desc = "Goes to the previous page.")
    private val noUnequip by switch("Disable unequip", desc = "Prevents using a wardrobe keybind to unequip a wardrobe. Does not prevent unequip keybind or normal clicking.")

    private val advanced by text("Keybinds")
    private val wardrobeKeys = (1..9).map { i ->
        KeybindComponent("Slot $i", CatKeys.KEY_0 + i, "Slot $i on the menu.").childOf(::advanced).value
    }

    private val wardrobeRegex = Regex("Wardrobe \\((\\d)/(\\d)\\)")

    init {
        on<GuiEvent.Click> {
            if (screen is AbstractContainerScreen<*> && onClick(screen, button)) cancel()
        }

        on<GuiEvent.Key> {
            if (screen is AbstractContainerScreen<*> && onClick(screen, this.key)) cancel()
        }
    }

    private fun onClick(screen: AbstractContainerScreen<*>, keyCode: Int): Boolean {
        val (current, total) = wardrobeRegex.find(screen.title?.string ?: "")?.destructured?.let {
            (it.component1().toIntOrNull() ?: 1) to (it.component2().toIntOrNull() ?: 1)
        } ?: return false

        val equippedIndex =
            screen.menu.slots.subList(36, 45)
                .indexOfFirst { it.item?.loreString?.contains("equipped", ignoreCase = true) == true }
                .takeIf { it != -1 }
                ?.plus(36)

        val slot = when (keyCode) {
            nextPageKeybind.key ->
                if (current < total) 53
                else return false.also { modMessage("§cYou are already on the last page.") }

            previousPageKeybind.key ->
                if (current > 1) 45
                else return false.also { modMessage("§cYou are already on the first page.") }

            unequipKeybind.key ->
                equippedIndex
                    ?: return false.also { modMessage("§cCouldn't find equipped armor.") }

            else -> {
                val wdIndex = wardrobeKeys.indexOfFirst { it.key == keyCode }.takeIf { it != -1 } ?: return false
                val targetSlot = wdIndex + 36

                if (equippedIndex == targetSlot && noUnequip)
                    return false.also { modMessage("§cArmor already equipped.") }

                targetSlot
            }
        }

        mc.player?.clickSlot(slot, screen.menu.containerId)
        return true
    }
}