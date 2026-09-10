package quoi.api.skyblock.dungeon.enums

/**
 * from OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/OdinFabric/blob/main/src/main/kotlin/com/odtheking/odin/utils/skyblock/dungeon/DungeonEnums.kt
 */
enum class Blessing(
    var regex: Regex,
    val displayString: String,
    var current: Int = 0
) {
    POWER(Regex("Blessing of Power (X{0,3}(IX|IV|V?I{0,3}))"), "Power"),
    LIFE(Regex("Blessing of Life (X{0,3}(IX|IV|V?I{0,3}))"), "Life"),
    WISDOM(Regex("Blessing of Wisdom (X{0,3}(IX|IV|V?I{0,3}))"), "Wisdom"),
    STONE(Regex("Blessing of Stone (X{0,3}(IX|IV|V?I{0,3}))"), "Stone"),
    TIME(Regex("Blessing of Time (V)"), "Time");

    fun reset() {
        current = 0
    }
}