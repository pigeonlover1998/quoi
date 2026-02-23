package quoi.module.impl.misc

import quoi.QuoiMod.scope
import quoi.api.commands.internal.GreedyString
import quoi.api.events.GuiEvent
import quoi.api.input.CatKeys
import quoi.config.Config
import quoi.module.Module
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.DropdownSetting
import quoi.module.settings.impl.KeybindSetting
import quoi.module.settings.impl.MapSetting
import quoi.utils.ChatUtils.button
import quoi.utils.ChatUtils.literal
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.ItemUtils.loreString
import quoi.utils.skyblock.ItemUtils.petHeldItem
import quoi.utils.skyblock.ItemUtils.skyblockId
import quoi.utils.skyblock.ItemUtils.skyblockUuid
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.PlayerUtils.clickSlot
import quoi.utils.skyblock.player.PlayerUtils.getContainerItemsClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.world.item.ItemStack

object PetKeybinds : Module(
    name = "Pet Keybinds",
    desc = "Keybinds for the pets menu. (/petkeybinds)"
) {
    private val unequipKeybind by KeybindSetting("Unequip", desc = "Unequips the current pet.")
    private val nextPageKeybind by KeybindSetting("Next page", desc = "Goes to the next page.")
    private val previousPageKeybind by KeybindSetting("Previous page", desc = "Goes to the previous page.")
    private val noUnequip by BooleanSetting("Disable unequip", desc = "Prevents using a pets keybind to unequip a pet. Does not prevent unequip keybind or normal clicking.")
    private val closeIfAlreadyEquipped by BooleanSetting("Close if already equipped", desc = "If the pet is already equipped, closes the Pets menu instead.")

    private val advanced by DropdownSetting("Keybinds").collapsible()
    private val petKeys = (1..9).map { i ->
        KeybindSetting("Pet $i", CatKeys.KEY_0 + i, "Pet $i on the list.").withDependency(advanced).value
    }


    private val petsRegex = Regex("Pets(?: \\((\\d)/(\\d)\\))?")

    val petMap by MapSetting("PetKeys map", mutableMapOf<String, String>())

    private var petsCache = emptyList<ItemStack>()

    const val LIST_ID = 67
    const val GET_ID = 69

    init {

        val petCommand = command.sub("petkeybinds").description("Pet Keybinds module settings.")

//        petCommand.sub("summontest") { uuidName: GreedyString ->
//            val (uuid, name) = uuidName.string.split(" ", limit = 2)
//
//            scope.launch(Dispatchers.IO) {
//                val msg = if (summonPet(uuid)) "Good" else "no good"
//                modMessage(msg)
//            }
//        }.suggests { petMap.entries.map { (uuid, name) -> "$uuid $name" } }

        petCommand.sub("clear") {
            petMap.clear()
            Config.save()
        }.description("Clears the pet list.")

        petCommand.sub("list") {
            if (petMap.isEmpty()) return@sub modMessage("Pet list is empty!")
            modMessage(petMap.asPet().toClickable("list"), LIST_ID)
        }.description("Shows the pet list.")

        petCommand.sub("get") {
            scope.launch(Dispatchers.IO) {
                petsCache = getPets()
                if (petsCache.isEmpty()) return@launch
                modMessage(petsCache.asPet().toClickable("get"), GET_ID)
            }
        }.description("Gets pets menu pets.")

        petCommand.sub("add") {
            val item = if (mc.player?.mainHandItem?.skyblockId == "PET") mc.player?.mainHandItem else null
            val uuid = item?.skyblockUuid ?: return@sub modMessage("§cYou can only add pets to the pet list!")
            if (petMap.size >= 9) return@sub modMessage("§cYou cannot add more than 9 pets to the list. Remove a pet using §e/petkeys remove §cor clear the list using §e/petkeys clear§c.")
            if (uuid in petMap) return@sub modMessage("§cThis pet is already in the list!")

            val name = item.displayName.string.petName
            petMap[uuid] = name
            modMessage("§aAdded &r$name&a to the pet list in position §6${petMap.keys.indexOf(uuid) + 1}§a!")
            Config.save()
        }.description("Adds the pet you're holding to the pet list.")

        petCommand.sub("addfromuuidname") { source: String, uuid: String, name: GreedyString ->
            if (uuid in petMap) return@sub modMessage("§cThis pet is already in the list!")

            petMap[uuid] = name.string
//            modMessage("&aAdded &r$name&a to the pet list in position ${petMap.keys.indexOf(uuid) + 1}!")
            Config.save()
            when (source) {
                "list" -> modMessage(petMap.asPet().toClickable("list"), LIST_ID)
                "get"  -> modMessage(petsCache.asPet().toClickable("get"), GET_ID)
            }
        }

        petCommand.sub("removefromuuidname") { source: String, uuid: String, name: GreedyString ->
            if (uuid !in petMap) return@sub modMessage("§cThis pet is not in the list!")

            petMap.remove(uuid)
//            modMessage("&aRemoved &r$name&a pet from the pet list!")
            Config.save()
            when (source) {
                "list" -> modMessage(petMap.asPet().toClickable("list"), LIST_ID)
                "get"  -> modMessage(petsCache.asPet().toClickable("get"), GET_ID)
            }
        }

        petCommand.sub("remove") { uuidName: GreedyString ->
            val (uuid, name) = uuidName.string.split(" ", limit = 2)
            if (uuid !in petMap) return@sub modMessage("This pet is not in the list!")
            petMap.remove(uuid)
            modMessage("&aRemoved &r$name&a from the pet list!")
            Config.save()
        }.description("Removes the pet from the pet list.").suggests { petMap.entries.map { (uuid, name) -> "$uuid $name" } }

        on<GuiEvent.Click> {
            if (screen is AbstractContainerScreen<*> && onClick(screen, button)) cancel()
        }

        on<GuiEvent.Key> {
            if (screen is AbstractContainerScreen<*> && onClick(screen, this.key)) cancel()
        }
    }

    fun List<Pet>.toClickable(source: String): MutableComponent {
        val result = literal("Pet list:\n")
        this.forEachIndexed { i, (uuid, name, heldItem) ->
            val symbol = if (uuid !in petMap) "&a[✔]" else "&c[x]"
            val command = if (uuid !in petMap) "addfromuuidname" else "removefromuuidname"
            val hoverText = if (uuid !in petMap) "Click to add!" else "Click to remove!"

            result.append(button(symbol, "/petkeybinds $command $source $uuid $name", hoverText))
            result.append(literal(" "))

            val heldStr = if (heldItem != null) " &7($heldItem)" else ""
            result.append(
                literal("&6$name$heldStr").withStyle(
                    Style.EMPTY.withHoverEvent(HoverEvent.ShowText(literal("$uuid")))
                )
            )
            if (i != size - 1) result.append(literal("\n"))
        }
        return result
    }

    private val String.petName
        get() = this.noControlCodes.replace(Regex("""⭐?\s*\[Lvl \d+] """), "").trim('[', ']')

    private fun onClick(screen: AbstractContainerScreen<*>, keyCode: Int): Boolean {
        val (current, total) = petsRegex.find(screen.title?.string ?: "")?.destructured?.let {
            (it.component1().toIntOrNull() ?: 1) to (it.component2().toIntOrNull() ?: 1)
        } ?: return false
        var slot = when (keyCode) {
            nextPageKeybind.key ->
                if (current < total) 53
                else return false.also { modMessage("§cYou are already on the last page.") }

            previousPageKeybind.key ->
                if (current > 1) 45
                else return false.also { modMessage("§cYou are already on the first page.") }

            unequipKeybind.key ->
                screen.menu.slots.subList(10, 43)
                    .indexOfFirst { it.item?.loreString?.contains("Click to despawn!") == true }
                    .takeIf { it != -1 }?.plus(10) ?: return false.also { modMessage("§cCouldn't find equipped pet") }

            else -> {
                val petIndex = petKeys.indexOfFirst { it.key == keyCode }.takeIf { it != -1 } ?: return false
                petMap.entries.elementAtOrNull(petIndex)?.let { (uuid, _) ->
                    screen.menu.slots.subList(10, 43).indexOfFirst { it?.item?.skyblockUuid == uuid }
                }?.takeIf { it != -1 }?.plus(10)
                    ?: return false//.also { modMessage("§cCouldn't find matching pet or there is no pet in that position.") }
            }
        }

        if (screen.menu.slots[slot].item?.loreString?.contains("Click to despawn!") == true && unequipKeybind.key != keyCode) {
//            modMessage("§cThat pet is already equipped!")
            if (closeIfAlreadyEquipped) slot = 49
            else if (noUnequip) return false
        }

        mc.player?.clickSlot(slot, screen.menu.containerId)
        return true
    }

    private suspend fun summonPet(uuid: String): Boolean {
        return PlayerUtils.getContainerItemsClick("petsmenu", "Pets", uuid = uuid, lore = "Left-click to summon!")
    }

    private suspend fun getPets(timeout: Int = 20): List<ItemStack> {
        val pets = getContainerItemsClose("petsmenu", "Pets", timeout = timeout).toMutableList()
        for (i in pets.indices) {
            if (i !in 9..<45 || i % 9 == 0 || i % 9 == 8) {
                pets[i] = null
            }
        }

        return pets.filterNotNull()
    }

    fun List<ItemStack>.asPet(): List<Pet> = map { stack ->
        Pet(
            stack.skyblockUuid,
            stack.displayName.string.petName,
            stack.petHeldItem?.replace("PET_ITEM_", "") ?: "NONE"
        )
    }

    fun Map<String, String>.asPet(): List<Pet> = map { (uuid, name) ->
        Pet(uuid, name)
    }

    data class Pet(val uuid: String?, val name: String, val heldItem: String? = null)
}