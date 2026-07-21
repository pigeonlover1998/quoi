package quoi.utils.skyblock.player.container.task

import net.minecraft.world.item.ItemStack

/**
 * Represents a target slot for [ContainerTask]s
 */
sealed interface MenuSlot {
    val inContainer: Boolean? // true = container, false = inventory, null = vanilla
}

/**
 * specific slot by index
 */
class IndexSlot(val index: Int, override val inContainer: Boolean?) : MenuSlot

/**
 * searches for an item that matches [predicate]
 */
class ItemSlot(val predicate: (ItemStack) -> Boolean, override val inContainer: Boolean?) : MenuSlot {
    /**
     * Only searches in open container
     */
    val menu: ItemSlot get() = ItemSlot(predicate, true)

    /**
     * Only searches in inventory
     */
    val inv: ItemSlot get() = ItemSlot(predicate, false)
}

// no filter
inline val Int.any: MenuSlot get() = IndexSlot(this, null)
// container only
inline val Int.menu: MenuSlot get() = IndexSlot(this, true)
// inventory only
inline val Int.inv: MenuSlot get() = IndexSlot(this, false)

inline val String.any: MenuSlot get() = item { it.displayName.string.contains(this, true) }
inline val String.menu: MenuSlot get() = item { it.displayName.string.contains(this, true) }.menu
inline val String.inv: MenuSlot get() = item { it.displayName.string.contains(this, true) }.inv

fun item(predicate: (ItemStack) -> Boolean) = ItemSlot(predicate, null)