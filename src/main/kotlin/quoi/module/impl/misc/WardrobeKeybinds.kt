package quoi.module.impl.misc

import quoi.api.events.GuiEvent
import quoi.api.input.CatKeys
import quoi.module.Module
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.DropdownSetting
import quoi.module.settings.impl.KeybindSetting
import quoi.utils.ChatUtils.modMessage
import quoi.utils.skyblock.ItemUtils.loreString
import quoi.utils.skyblock.player.PlayerUtils.clickSlot
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

object WardrobeKeybinds : Module(
    "Wardrobe Keybinds"
) {
    private val unequipKeybind by KeybindSetting("Unequip", desc = "Unequips the current slot.")
    private val nextPageKeybind by KeybindSetting("Next page", desc = "Goes to the next page.")
    private val previousPageKeybind by KeybindSetting("Previous page", desc = "Goes to the previous page.")
    private val noUnequip by BooleanSetting("Disable unequip", desc = "Prevents using a wardrobe keybind to unequip a wardrobe. Does not prevent unequip keybind or normal clicking.")

    private val advanced by DropdownSetting("Keybinds").collapsible()
    private val wardrobeKeys = (1..9).map { i ->
        KeybindSetting("Slot $i", CatKeys.KEY_0 + i, "Slot $i on the menu.").withDependency(advanced).value
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