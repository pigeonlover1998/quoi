package quoi.api.skyblock.dungeon.enums

import quoi.api.colour.Colour

/**
 * from OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/OdinFabric/blob/main/src/main/kotlin/com/odtheking/odin/utils/skyblock/dungeon/DungeonEnums.kt
 */

/**
 * Enumeration representing player classes in a dungeon setting.
 *
 * Each class is associated with a specific code and color used for formatting in the game. The classes include Archer,
 * Mage, Berserk, Healer, and Tank.
 *
 * @property colour The color associated with the class.
 * @property defaultQuadrant The default quadrant for the class.
 * @property priority The priority of the class.
 *
 */
enum class DungeonClass(
    private val hypixelColour: Colour,
    private val hypixelColourCode: Char,
    val defaultQuadrant: Int,
    var priority: Int,
) {
    Archer(Colour.MINECRAFT_GOLD, '6', 0, 2),
    Berserk(Colour.MINECRAFT_DARK_RED, '4', 1, 0),
    Healer(Colour.MINECRAFT_LIGHT_PURPLE, 'd', 2, 2),
    Mage(Colour.MINECRAFT_AQUA, 'b', 3, 2),
    Tank(Colour.MINECRAFT_DARK_GREEN, '2', 3, 1),
    Unknown(Colour.WHITE, 'f', 0, 0);

    val colour: Colour
        get() = hypixelColour

    val colourCode: Char
        get() = hypixelColourCode
}