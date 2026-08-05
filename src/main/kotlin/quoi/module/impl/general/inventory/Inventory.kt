package quoi.module.impl.general.inventory

import quoi.module.Module
import quoi.module.impl.general.inventory.impl.InventoryHud
import quoi.module.impl.general.inventory.impl.InventorySearch

object Inventory : Module(
    "Inventory",
    desc = "Various quality of life features for inventory GUIs"
) {
    @Suppress("unused")
    private val feat = listOf(
        InventorySearch, InventoryHud
    )
}