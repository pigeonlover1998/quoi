package quoi.api.skyblock.dungeon

import quoi.QuoiMod.mc
import quoi.QuoiMod.scope
import quoi.api.colour.Colour
import quoi.api.events.PacketEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventBus.on
import quoi.api.skyblock.Island
import quoi.api.skyblock.Location
import quoi.api.skyblock.dungeon.components.DiscoveredRoom
import quoi.api.skyblock.dungeon.components.Door
import quoi.api.skyblock.dungeon.components.Room
import quoi.api.skyblock.dungeon.map.RoomRegistry
import quoi.api.skyblock.dungeon.map.WorldScanner
import quoi.api.skyblock.dungeon.map.utils.MapItemUtils
import quoi.api.skyblock.dungeon.map.utils.ScanUtils
import quoi.module.impl.dungeon.LeapMenu
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.equalsOneOf
import quoi.utils.romanToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.network.protocol.game.ClientboundTabListPacket
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SkullBlock
import net.minecraft.world.level.block.entity.SkullBlockEntity
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/OdinFabric/blob/main/src/main/kotlin/com/odtheking/odin/utils/skyblock/dungeon/DungeonUtils.kt
 *           https://github.com/odtheking/OdinFabric/blob/main/src/main/kotlin/com/odtheking/odin/utils/skyblock/dungeon/DungeonListener.kt
 */
object Dungeon {

    inline val inDungeons: Boolean
        get() = Location.currentArea.isArea(Island.Dungeon)

    inline val inClear: Boolean
        get() = inDungeons && !inBoss

    var floor: Floor? = null
        private set

    inline val inBoss: Boolean
        get() = getBoss()

    var inP3: Boolean = false
        private set

    var currentP3Section: Int = 0
        private set

    var p3TermsCompleted: Boolean = false
        private set

    var p3GateDestroyed: Boolean = false
        private set

    inline val secretCount: Int
        get() = dungeonStats.secretsFound

    inline val knownSecrets: Int
        get() = dungeonStats.knownSecrets

    inline val secretPercentage: Float
        get() = dungeonStats.secretsPercent

    inline val totalSecrets: Int
        get() = if (secretCount == 0 || secretPercentage == 0f) 0 else floor(100 / secretPercentage * secretCount + 0.5).toInt()

    inline val deathCount: Int
        get() = dungeonStats.deaths

    inline val cryptCount: Int
        get() = dungeonStats.crypts

    inline val openRoomCount: Int
        get() = dungeonStats.openedRooms

    inline val completedRoomCount: Int
        get() = dungeonStats.completedRooms

    inline val percentCleared: Int
        get() = dungeonStats.percentCleared

    inline val totalRooms: Int
        get() = if (completedRoomCount == 0 || percentCleared == 0) 0 else floor((completedRoomCount / (percentCleared * 0.01).toFloat()) + 0.4).toInt()

    var puzzles = ArrayList<Puzzle>()
        private set

    inline val puzzleCount: Int
        get() = dungeonStats.puzzleCount

    inline val dungeonTime: String
        get() = dungeonStats.elapsedTime

//    inline val currentRoomName: String
//        get() = DungeonListener.currentRoom?.data?.name ?: "Unknown"

    var dungeonTeammates: ArrayList<DungeonPlayer> = ArrayList(5)
        private set

    var dungeonTeammatesNoSelf: List<DungeonPlayer> = ArrayList(4)
        private set

    var leapTeammates: List<DungeonPlayer> = ArrayList(4)
        private set

    inline val currentDungeonPlayer: DungeonPlayer
        get() = dungeonTeammates.find { it.name == mc.player?.name?.string } ?:
        DungeonPlayer(mc.player?.name?.string ?: "Unknown", DungeonClass.Unknown, 0, null)

    inline val isDead: Boolean
        get() = currentDungeonPlayer.isDead

    inline val doorOpener: String
        get() = dungeonStats.doorOpener

    inline val mimicKilled: Boolean
        get() = dungeonStats.mimicKilled

    inline val princeKilled: Boolean
        get() = dungeonStats.princeKilled

    inline val currentRoom: Room?
        get() = ScanUtils.currentRoom

    inline val rooms: Array<Room?>
        get() = ScanUtils.rooms

    inline val discoveredRooms: MutableMap<String, DiscoveredRoom>
        get() = ScanUtils.discoveredRooms

    inline val uniqueRooms: MutableSet<Room>
        get() = ScanUtils.uniqueRooms

    inline val doors: Array<Door?>
        get() = ScanUtils.doors

    inline val uniqueDoors: MutableSet<Door>
        get() = ScanUtils.uniqueDoors
//
//    inline val passedRooms: Set<Room>
//        get() = DungeonListener.passedRooms

    var isPaul: Boolean = false
        private set

    inline val getBonusScore: Int
        get() {
            var score = cryptCount.coerceAtMost(5)
            if (mimicKilled) score += 2
            if (princeKilled) score += 1
//            if ((isPaul && togglePaul == 0) || togglePaul == 2) score += 10
            return score
        }

    inline val bloodDone: Boolean
        get() = dungeonStats.bloodDone

    inline val score: Int
        get() {
            val completed = completedRoomCount + (if (!bloodDone) 1 else 0) + (if (!inBoss) 1 else 0)
            val total = if (totalRooms != 0) totalRooms else 36

            val exploration = floor?.let {
                floor((secretPercentage / it.secretPercentage) / 100f * 40f).coerceIn(0f, 40f).toInt() +
                        floor(completed.toFloat() / total * 60f).coerceIn(0f, 60f).toInt()
            } ?: 0

            val skillRooms = floor(completed.toFloat() / total * 80f).coerceIn(0f, 80f).toInt()
            val puzzlePenalty = (puzzleCount - puzzles.count { it.status == PuzzleStatus.Completed }) * 10

            return exploration + (20 + skillRooms - puzzlePenalty - (deathCount * 2 - 1).coerceAtLeast(0)).coerceIn(
                20,
                100
            ) + getBonusScore + 100
        }

    inline val neededSecretsAmount: Int
        get() =
            floor?.let {
                ceil(
                    (totalSecrets * it.secretPercentage) * (40 - getBonusScore + (deathCount * 2 - 1).coerceAtLeast(
                        0
                    )) / 40f
                ).toInt()
            } ?: 0


    var dungeonStats = DungeonStats()
        private set

    private var expectingBloodUpdate = false

    fun init() {
        RoomRegistry.loadRooms()
        WorldScanner.init()
        MapItemUtils.init()

        on<WorldEvent.Change> {
            Blessing.entries.forEach { it.reset() }
            dungeonTeammatesNoSelf = emptyList()
            dungeonStats = DungeonStats()
            expectingBloodUpdate = false
            leapTeammates = emptyList()
            dungeonTeammates.clear()
            puzzles.clear()
            floor = null
            isPaul = false

            inP3 = false
            currentP3Section = 0
            p3TermsCompleted = false
            p3GateDestroyed = false

            MapItemUtils.reset()
            WorldScanner.reset()
            ScanUtils.reset()
        }

//        on<RoomEnterEvent>(priority = 100) {
//            val room = room?.takeUnless { room -> passedRooms.any { it.data.name == room.data.name } } ?: return@on
//            dungeonStats.knownSecrets += room.data.secrets
//        }

        on<PacketEvent.Received> {
            with(packet) {
                when (this) {
                    is ClientboundPlayerInfoUpdatePacket -> {
                        if (actions().none {
                                it.equalsOneOf(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                                    ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)
                            }) return@on
                        val tabListEntries = entries()
                            ?.mapNotNull { it.displayName }
                            ?.ifEmpty { return@on }
                            ?: return@on

                        val stringEntries = tabListEntries.map { it.string }
                        val colouredEntries = tabListEntries.map { it.string to Colour.RGB(it.style.color?.value ?: Colour.WHITE.rgb) }

                        updateDungeonTeammates(colouredEntries)
                        updateDungeonStats(stringEntries)
                        getDungeonPuzzles(stringEntries)
                    }

                    is ClientboundSetPlayerTeamPacket -> {
                        val team = parameters?.orElse(null) ?: return@on

                        val text = team.playerPrefix?.string?.noControlCodes?.plus(team.playerSuffix?.string?.noControlCodes) ?: return@on

                        floorRegex.find(text)?.groupValues?.get(1)?.let {
                            scope.launch(Dispatchers.IO) { isPaul = false /*hasBonusPaulScore()*/ } // fixme
                            floor = Floor.valueOf(it)
                        }

                        clearedRegex.find(text)?.groupValues?.get(1)?.toIntOrNull()?.let {
                            if (dungeonStats.percentCleared != it && expectingBloodUpdate) dungeonStats.bloodDone = true
                            dungeonStats.percentCleared = it
                        }
                        dungeonTeammates.find { it.name == mc.player?.name?.string }?.apply {
                            isDead = mc.player?.inventory?.getItem(0)?.displayName?.string?.contains("Haunt") == true
                        }
                    }

                    is ClientboundTabListPacket -> {
                        Blessing.entries.forEach { blessing ->
                            blessing.regex.find(footer?.string ?: return@forEach)
                                ?.let { blessing.current = romanToInt(it.groupValues[1]) }
                        }
                    }

                    is ClientboundSystemChatPacket -> {
                        val message = content?.string?.noControlCodes ?: return@on
                        if (expectingBloodRegex.matches(message)) expectingBloodUpdate = true
                        doorOpenRegex.find(message)?.let { dungeonStats.doorOpener = it.groupValues[1] }
                        deathRegex.find(message)?.let { match ->
                            dungeonTeammates.find { teammate ->
                                teammate.name == (match.groupValues[1].takeUnless { it == "You" } ?: mc.player?.name?.string)
                            }?.deaths?.inc()
                        }

                        when (message) {
                            "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!", "The Core entrance is opening!" -> {
                                inP3 = false
                                currentP3Section = 0
                                resetP3State()
                            }
                            "[BOSS] Goldor: Who dares trespass into my domain?" -> {
                                inP3 = true
                                currentP3Section = 1
                                resetP3State()
                            }
                        }

                        if (inBoss) {
                            processP3Events(message)
                        }

                        when (partyMessageRegex.find(message)?.groupValues?.get(1)?.lowercase() ?: return@on) {
                            "mimic killed", "mimic slain", "mimic killed!",
                            "mimic dead", "mimic dead!", $$"$skytils-dungeon-score-mimic$", /*Mimic.mimicMessage*/ ->
                                dungeonStats.mimicKilled = true

                            "prince killed", "prince slain", "prince killed!",
                            "prince dead", "prince dead!", $$"$skytils-dungeon-score-prince$", /*Mimic.princeMessage*/ ->
                                dungeonStats.princeKilled = true

                            "blaze done!", "blaze done", "blaze puzzle solved!" ->
                                puzzles.find { it == Puzzle.BLAZE }.let { it?.status = PuzzleStatus.Completed }
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks if the current dungeon floor number matches any of the specified options.
     *
     * @param options The floor number options to compare with the current dungeon floor.
     * @return `true` if the current dungeon floor matches any of the specified options, otherwise `false`.
     */
    fun isFloor(vararg options: Int): Boolean {
        return floor?.floorNumber?.let { it in options } ?: false
    }

    /**
     * Gets the current phase of floor 7 boss.
     *
     * @return The current phase of floor 7 boss, or `null` if the player is not in the boss room.
     */
    fun getF7Phase(): M7Phases {
        if ((!isFloor(7) || !inBoss) && Location.onHypixel) return M7Phases.Unknown

        with(mc.player ?: return M7Phases.Unknown) {
            return when {
                y > 210 -> M7Phases.P1
                y > 155 -> M7Phases.P2
                y > 100 -> M7Phases.P3
                y > 45 -> M7Phases.P4
                else -> M7Phases.P5
            }
        }
    }

    fun getMageCooldownMultiplier(): Double {
        return if (currentDungeonPlayer.clazz != DungeonClass.Mage) 1.0
        else 1 - 0.25 - (floor(currentDungeonPlayer.clazzLvl / 2.0) / 100) * if (dungeonTeammates.count { it.clazz == DungeonClass.Mage } == 1) 2 else 1
    }

    /**
     * Gets the new ability cooldown after mage cooldown reductions.
     * @param baseSeconds The base cooldown of the ability in seconds. Eg 10
     * @return The new time
     */
    fun getAbilityCooldown(baseSeconds: Long): Long {
        return (baseSeconds * getMageCooldownMultiplier()).roundToLong()
    }

    private val tablistRegex = Regex("^\\[(\\d+)] (?:\\[\\w+] )*(\\w+) .*?\\((\\w+)(?: (\\w+))*\\)$")

    private fun getDungeonTeammates(previousTeammates: ArrayList<DungeonPlayer>, tabList: List<Pair<String, Colour>>): ArrayList<DungeonPlayer> {
        for ((line, colour) in tabList) {
            val (_, name, clazz, clazzLevel) = tablistRegex.find(line)?.destructured ?: continue

            previousTeammates.find { it.name == name }?.let { player -> player.isDead = clazz == "DEAD" }
                ?: previousTeammates.add(
                    DungeonPlayer(
                        name,
                        DungeonClass.entries.find { it.name == clazz } ?: continue,
                        romanToInt(clazzLevel),
                        mc.connection?.getPlayerInfo(name)?.skin?.body?.texturePath(),
                        colour = colour
                    )
                )
        }
        return previousTeammates
    }

    private const val WITHER_ESSENCE_ID = "e0f3e929-869e-3dca-9504-54c666ee6f23"
    private const val REDSTONE_KEY = "fed95410-aba1-39df-9b95-1d4f361eb66e"

    /**
     * Determines whether a given block state and position represent a secret location.
     *
     * This function checks if the specified block state and position correspond to a secret location based on certain criteria.
     * It considers blocks such as chests, trapped chests, and levers as well as player skulls with a specific player profile ID.
     *
     * @param state The block state to be evaluated for secrecy.
     * @param pos The position (BlockPos) of the block in the world.
     * @return `true` if the specified block state and position indicate a secret location, otherwise `false`.
     */
    fun isSecret(state: BlockState, pos: BlockPos): Boolean {
        return when {
            state.block.equalsOneOf(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.LEVER) -> true
            state.block is SkullBlock ->
                (mc.level?.getBlockEntity(pos) as? SkullBlockEntity)?.ownerProfile?.partialProfile()?.id
                    ?.toString()?.equalsOneOf(WITHER_ESSENCE_ID, REDSTONE_KEY) ?: false

            else -> false
        }
    }

    fun isProtectedBlock(pos: BlockPos): Boolean {
        val level = mc.level ?: return true
        if (level.getBlockEntity(pos) != null) return true

        val state = level.getBlockState(pos)

        return state.block in blacklistedDBBlocks
    }

    fun getBoss(): Boolean = with(mc.player) {
        if (this == null) return false
        when (floor?.floorNumber) {
            1 -> x > -71 && z > -39
            in 2..4 -> x > -39 && z > -39
            in 5..6 -> x > -39 && z > -7
            7 -> x > -7 && z > -7
            else -> false
        }
    }

    private fun processP3Events(msg: String) {
        val termMatch = REGEX_TERM_COMPLETED.find(msg)
        if (termMatch != null) {
            val completed = termMatch.groupValues[4].toIntOrNull() ?: 0
            val total = termMatch.groupValues[5].toIntOrNull() ?: 0
            if (completed == total) {
                if (p3GateDestroyed) advanceP3Section()
                else p3TermsCompleted = true
            }
        }

        if (REGEX_GATE_DESTROYED.matches(msg)) {
            p3GateDestroyed = true
            if (p3TermsCompleted) advanceP3Section()
        }
    }

    private fun advanceP3Section() {
        currentP3Section++
        resetP3State()
    }

    private fun resetP3State() {
        p3TermsCompleted = false
        p3GateDestroyed = false
    }

    private fun getDungeonPuzzles(tabList: List<String>) {
        for (entry in tabList) {
            val (name, status) = puzzleRegex.find(entry)?.destructured ?: continue
            val puzzle = Puzzle.entries.find { it.displayName == name }?.takeIf { it != Puzzle.UNKNOWN } ?: continue
            if (puzzle !in puzzles) puzzles.add(puzzle)

            puzzle.status = when (status) {
                "✖" -> PuzzleStatus.Failed
                "✔" -> PuzzleStatus.Completed
                "✦" -> PuzzleStatus.Incomplete
                else -> continue
            }
        }
    }

    private fun updateDungeonStats(text: List<String>) {
        for (entry in text) {
            with(dungeonStats) {
                secretsPercent = secretPercentRegex.find(entry)?.groupValues?.get(1)?.toFloatOrNull() ?: secretsPercent
                completedRooms = completedRoomsRegex.find(entry)?.groupValues?.get(1)?.toIntOrNull() ?: completedRooms
                secretsFound = secretCountRegex.find(entry)?.groupValues?.get(1)?.toIntOrNull() ?: secretsFound
                openedRooms = openedRoomsRegex.find(entry)?.groupValues?.get(1)?.toIntOrNull() ?: openedRooms
                puzzleCount = puzzleCountRegex.find(entry)?.groupValues?.get(1)?.toIntOrNull() ?: puzzleCount
                deaths = deathsRegex.find(entry)?.groupValues?.get(1)?.toIntOrNull() ?: deaths
                crypts = cryptRegex.find(entry)?.groupValues?.get(1)?.toIntOrNull() ?: crypts
                elapsedTime = timeRegex.find(entry)?.groupValues?.get(1) ?: elapsedTime
            }
        }
    }

    private fun updateDungeonTeammates(tabList: List<Pair<String, Colour>>) {
        dungeonTeammates = getDungeonTeammates(dungeonTeammates, tabList)
        dungeonTeammatesNoSelf = dungeonTeammates.filter { it.name != mc.player?.name?.string }

        leapTeammates =
            when (LeapMenu.sorting.selected) {
                "Class" -> dungeonTeammatesNoSelf.sortedWith(compareBy({ it.clazz.ordinal }, { it.name }))
                "Name" -> dungeonTeammatesNoSelf.sortedBy { it.name }
                "Custom" -> {
                    if (LeapMenu.fillEmpty) {
                        val remaining = dungeonTeammatesNoSelf.toMutableList()
                        LeapMenu.customOrder.map { entry ->
                            if (entry == "_") DungeonPlayer.EMPTY
                            else remaining.firstOrNull { it.name.lowercase().equals(entry, true) }?.also { remaining.remove(it) } ?: DungeonPlayer.EMPTY
                        }.map { if (it == DungeonPlayer.EMPTY && remaining.isNotEmpty()) remaining.removeAt(0) else it }
                    } else {
                        LeapMenu.customOrder.map { entry ->
                            if (entry == "_") DungeonPlayer.EMPTY
                            else dungeonTeammatesNoSelf.firstOrNull { it.name.lowercase().equals(entry, true) } ?: DungeonPlayer.EMPTY
                        }
                    }
                }
                else -> dungeonTeammatesNoSelf
            }
    }

    val dungeonItemDrops = setOf(
        "Health Potion VIII Splash Potion", "Healing Potion 8 Splash Potion", "Healing Potion VIII Splash Potion", "Healing VIII Splash Potion", "Healing 8 Splash Potion",
        "Decoy", "Inflatable Jerry", "Spirit Leap", "Trap", "Training Weights", "Defuse Kit", "Dungeon Chest Key", "Treasure Talisman", "Revive Stone", "Architect's First Draft",
        "Secret Dye", "Candycomb"
    )

    val blacklistedDBBlocks = setOf(
        Blocks.BARRIER, Blocks.BEDROCK, Blocks.COMMAND_BLOCK, Blocks.CHAIN_COMMAND_BLOCK,
        Blocks.REPEATING_COMMAND_BLOCK, Blocks.SKELETON_SKULL, Blocks.SKELETON_WALL_SKULL,
        Blocks.WITHER_SKELETON_SKULL, Blocks.WITHER_SKELETON_WALL_SKULL, Blocks.TNT,
        Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.END_PORTAL_FRAME, Blocks.END_PORTAL,
        Blocks.PISTON, Blocks.PISTON_HEAD, Blocks.STICKY_PISTON, Blocks.MOVING_PISTON,
        Blocks.LEVER, Blocks.STONE_BUTTON
    )

    private val REGEX_TERM_COMPLETED = Regex("^(.{1,16}) (activated|completed) a (terminal|lever|device)! \\((\\d)/(\\d)\\)$")
    private val REGEX_GATE_DESTROYED = Regex("^The gate has been destroyed!$")

    private val puzzleRegex = Regex("^ (\\w+(?: \\w+)*|\\?\\?\\?): \\[([✖✔✦])] ?(?:\\((\\w+)\\))?$")
    private val expectingBloodRegex = Regex("^\\[BOSS] The Watcher: You have proven yourself. You may pass.")
    private val doorOpenRegex = Regex("^(?:\\[\\w+] )?(\\w+) opened a (?:WITHER|Blood) door!")
    private val secretPercentRegex = Regex("^ Secrets Found: ([\\d.]+)%$")
    private val deathRegex = Regex("☠ (\\w{1,16}) .* and became a ghost\\.")
    private val timeRegex = Regex("^ Time: ((?:\\d+h ?)?(?:\\d+m ?)?\\d+s)$")
    private val completedRoomsRegex = Regex("^ Completed Rooms: (\\d+)$")
    private val clearedRegex = Regex("^Cleared: (\\d+)% \\(\\d+\\)$")
    private val secretCountRegex = Regex("^ Secrets Found: (\\d+)$")
    private val openedRoomsRegex = Regex("^ Opened Rooms: (\\d+)$")
    private val floorRegex = Regex("The Catacombs \\((\\w+)\\)$")
    private val partyMessageRegex = Regex("^Party > .*?: (.+)$")
    private val puzzleCountRegex = Regex("^Puzzles: \\((\\d+)\\)$")
    private val deathsRegex = Regex("^Team Deaths: (\\d+)$")
    private val cryptRegex = Regex("^ Crypts: (\\d+)$")

    data class DungeonStats(
        var secretsFound: Int = 0,
        var secretsPercent: Float = 0f,
        var knownSecrets: Int = 0,
        var crypts: Int = 0,
        var openedRooms: Int = 0,
        var completedRooms: Int = 0,
        var deaths: Int = 0,
        var percentCleared: Int = 0,
        var elapsedTime: String = "0s",
        var mimicKilled: Boolean = false,
        var princeKilled: Boolean = false,
        var doorOpener: String = "Unknown",
        var bloodDone: Boolean = false,
        var puzzleCount: Int = 0,
    )
}