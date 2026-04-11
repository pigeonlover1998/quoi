package quoi.utils.skyblock

import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import quoi.utils.StringUtils.noControlCodes

object ItemUtils {
    private val chargesRegex = Regex("Charges: (\\d+)/(\\d+)⸕")

    inline val ItemStack?.extraAttributes: CompoundTag?
        get() = this?.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)?.copyTag()

    inline val ItemStack?.skyblockId: String?
        get() = this.extraAttributes?.getString("id")?.orElse(null)

    inline val ItemStack?.skyblockUuid: String?
        get() = this.extraAttributes?.getString("uuid")?.orElse(null)

    inline val ItemStack?.petHeldItem: String? // no good
        get() = this?.extraAttributes?.getString("petInfo")?.orElse(null)?.let {
            parseToJsonElement(it).jsonObject["heldItem"]?.jsonPrimitive?.content
        }

    inline val ItemStack?.lore: List<String>?
        get() = this?.get(DataComponents.LORE)?.lines?.map { it.string }

    inline val ItemStack?.loreString: String?
        get() = this?.lore?.joinToString("\n")

    inline val ItemStack?.isShortbow: Boolean
        get() = this?.loreString?.noControlCodes?.contains("Shortbow: Instantly shoots!") == true

    inline val ItemStack.texture: String?
        get() = get(DataComponents.PROFILE)?.partialProfile()?.properties?.get("textures")?.firstOrNull()?.value

    inline val LocalPlayer.hasTerminator: Boolean
        get() {
            for (i in 0..8) {
                val stack = inventory.getItem(i)
                if (stack.skyblockId == "TERMINATOR") {
                    return true
                }
            }
            return false
        }

    fun getBreakerCharges(stack: ItemStack): Int {
        if (stack.isEmpty || stack.skyblockId != "DUNGEONBREAKER") return 0

        return stack.lore?.firstNotNullOfOrNull { line ->
            chargesRegex.find(line.noControlCodes)?.groupValues?.get(1)?.toIntOrNull()
        } ?: 0
    }

}