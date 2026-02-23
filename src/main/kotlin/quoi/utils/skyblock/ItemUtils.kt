package quoi.utils.skyblock

import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

object ItemUtils {
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

    val ItemStack.texture: String?
        get() = get(DataComponents.PROFILE)?.partialProfile()?.properties?.get("textures")?.firstOrNull()?.value
}