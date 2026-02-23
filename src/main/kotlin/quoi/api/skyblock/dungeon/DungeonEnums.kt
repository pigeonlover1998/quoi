package quoi.api.skyblock.dungeon

import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.api.skyblock.PlayerPosition
import quoi.api.skyblock.dungeon.components.Room
import quoi.api.skyblock.dungeon.map.MapItemScanner
import quoi.module.impl.dungeon.DungeonMap
import quoi.utils.equalsOneOf
import net.minecraft.client.player.LocalPlayer
import net.minecraft.resources.ResourceLocation
import java.util.*

/**
 * Data class representing a player in a dungeon, including their name, class, skin location, and associated player entity.
 *
 * @property name The name of the player.
 * @property clazz The player's class, defined by the [DungeonClass] enum.
 * @property locationSkin The resource location of the player's skin.
 * @property isDead The player's death status. Defaults to `false`.
 */
data class DungeonPlayer(
    val name: String,
    val clazz: DungeonClass,
    val clazzLvl: Int,
    val locationSkin: ResourceLocation?,
    var isDead: Boolean = false,
    var deaths: Int = 0,
    val colour: Colour = Colour.WHITE,

    var minRooms: Int = 0,
    var maxRooms: Int = 0,
    var inRender: Boolean = false,
    var currRoom: Room? = null,
    var lastRoom: Room? = null,
    var pos: PlayerPosition = PlayerPosition(),

    val clearedRooms: MutableMap<String, MutableMap<String, MapItemScanner.RoomClearInfo>> = mutableMapOf(
        "WHITE" to mutableMapOf(),
        "GREEN" to mutableMapOf()
    )
) {
    val entity: LocalPlayer? =
        mc.level?.entitiesForRendering()
            ?.filterIsInstance<LocalPlayer>()
            ?.find { it.gameProfile.name == name }

    val uuid: UUID? get() = entity?.uuid

    fun getGreenChecks() = clearedRooms["GREEN"] ?: mutableMapOf()
    fun getWhiteChecks() = clearedRooms["WHITE"] ?: mutableMapOf()

    companion object {
        val EMPTY = DungeonPlayer("Empty", DungeonClass.Unknown, 0, null)
    }
}


/**
 * Enumeration representing puzzles in a dungeon.
 *
 * @property displayName The display name of the puzzle.
 * @property status The current status of the puzzle. Defaults to `null`.
 */
enum class Puzzle(
    val displayName: String,
    var status: PuzzleStatus? = null
) {
    UNKNOWN("???"),
    BLAZE("Higher Or Lower"),
    BEAMS("Creeper Beams"),
    WEIRDOS("Three Weirdos"),
    TTT("Tic Tac Toe"),
    WATER_BOARD("Water Board"),
    TP_MAZE("Teleport Maze"),
    BOULDER("Boulder"),
    ICE_FILL("Ice Fill"),
    ICE_PATH("Ice Path"),
    QUIZ("Quiz"),
    BOMB_DEFUSE("Bomb Defuse");
}

sealed class PuzzleStatus {
    data object Completed : PuzzleStatus()
    data object Failed : PuzzleStatus()
    data object Incomplete : PuzzleStatus()
}

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
    val colour: Colour,
    val colourCode: Char,
    val defaultQuadrant: Int,
    var priority: Int,
) {
    Archer(Colour.MINECRAFT_GOLD, '6', 0, 2),
    Berserk(Colour.MINECRAFT_DARK_RED, '4', 1, 0),
    Healer(Colour.MINECRAFT_LIGHT_PURPLE, 'd', 2, 2),
    Mage(Colour.MINECRAFT_AQUA, 'b', 3, 2),
    Tank(Colour.MINECRAFT_DARK_GREEN, '2', 3, 1),
    Unknown(Colour.WHITE, 'f', 0, 0)
}

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

enum class M7Phases(val displayName: String) {
    P1("P1"), P2("P2"), P3("P3"), P4("P4"), P5("P5"), Unknown("Unknown");
}


enum class Checkmark(
    val texture: ResourceLocation?,
    val colorCode: String
) {
    NONE(null, "§7"),
    WHITE(ResourceLocation.fromNamespaceAndPath("quoi", "stellanav/clear/bloommapwhitecheck"), "§f"),
    GREEN(ResourceLocation.fromNamespaceAndPath("quoi", "stellanav/clear/bloommapgreencheck"), "§a"),
    FAILED(ResourceLocation.fromNamespaceAndPath("quoi", "stellanav/clear/bloommapfailedroom"), "§c"),
    UNEXPLORED(ResourceLocation.fromNamespaceAndPath("quoi", "stellanav/clear/bloommapquestionmark"), "§7"),
    UNDISCOVERED(null, "§7");
}

enum class RoomType(
    val displayName: String,
    val colour: Colour
) {
    NORMAL("Normal", DungeonMap.normalRoom),
    PUZZLE("Puzzle", DungeonMap.puzzleRoom),
    TRAP("Trap", DungeonMap.trapRoom),
    YELLOW("Yellow", DungeonMap.miniRoom),
    BLOOD("Blood", DungeonMap.bloodRoom),
    FAIRY("Fairy", DungeonMap.fairyRoom),
    RARE("Rare", DungeonMap.rareRoom),
    ENTRANCE("Entrance", DungeonMap.entranceRoom),
    UNKNOWN("Unknown", DungeonMap.unknownRoom);

    fun isNormal() = !this.equalsOneOf(PUZZLE, UNKNOWN)
}

enum class DoorType(
    val displayName: String,
    val colour: Colour
) {
    NORMAL("Normal", DungeonMap.normalDoor),
    WITHER("Wither", DungeonMap.witherDoor),
    BLOOD("Blood", DungeonMap.bloodDoor),
    ENTRANCE("Entrance", DungeonMap.entranceDoor);
}

enum class DoorState { UNDISCOVERED, DISCOVERED }