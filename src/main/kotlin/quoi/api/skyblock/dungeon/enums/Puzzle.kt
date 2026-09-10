package quoi.api.skyblock.dungeon.enums

/**
 * from OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/OdinFabric/blob/main/src/main/kotlin/com/odtheking/odin/utils/skyblock/dungeon/DungeonEnums.kt
 */

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