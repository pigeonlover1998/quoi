package quoi.api.skyblock.dungeon.enums

/**
 * from OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/OdinFabric/blob/main/src/main/kotlin/com/odtheking/odin/utils/skyblock/dungeon/DungeonEnums.kt
 */

/**
 * Enumeration representing different floors in a dungeon.
 *
 * This enum class defines various floors, including both regular floors (F1 to F7) and master mode floors (M1 to M7).
 * Each floor has an associated floor number and an indicator of whether it is a master mode floor.
 *
 * @property floorNumber The numerical representation of the floor, where E represents the entrance floor.
 * @property isMM Indicates whether the floor is a master mode floor (M1 to M7).
 * @property secretPercentage The percentage of secrets required.
 */
enum class Floor(val secretPercentage: Float = 1f) {
    E(0.3f),
    F1(0.3f),
    F2(0.4f),
    F3(0.5f),
    F4(0.6f),
    F5(0.7f),
    F6(0.85f),
    F7,
    M1,
    M2,
    M3,
    M4,
    M5,
    M6,
    M7;

    /**
     * Gets the numerical representation of the floor.
     *
     * @return The floor number. E has a floor number of 0, F1 to F7 have floor numbers from 1 to 7, and M1 to M7 have floor numbers from 1 to 7.
     */
    inline val floorNumber: Int
        get() {
            return when (this) {
                E -> 0
                F1, M1 -> 1
                F2, M2 -> 2
                F3, M3 -> 3
                F4, M4 -> 4
                F5, M5 -> 5
                F6, M6 -> 6
                F7, M7 -> 7
            }
        }

    /**
     * Indicates whether the floor is a master mode floor.
     *
     * @return `true` if the floor is a master mode floor (M1 to M7), otherwise `false`.
     */
    inline val isMM: Boolean
        get() {
            return when (this) {
                E, F1, F2, F3, F4, F5, F6, F7 -> false
                M1, M2, M3, M4, M5, M6, M7 -> true
            }
        }
}