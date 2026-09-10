package quoi.api.skyblock.dungeon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.common.ClientboundPingPacket
import net.minecraft.network.protocol.game.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SkullBlock
import net.minecraft.world.level.block.entity.SkullBlockEntity
import net.minecraft.world.level.block.state.BlockState
import quoi.QuoiMod.scope
import quoi.annotations.Init
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.DungeonEvent
import quoi.api.events.PacketEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.enums.Blessing
import quoi.api.skyblock.dungeon.enums.DungeonClass
import quoi.api.skyblock.dungeon.enums.DungeonPlayer
import quoi.api.skyblock.dungeon.enums.Floor
import quoi.api.skyblock.dungeon.enums.Puzzle
import quoi.api.skyblock.dungeon.enums.PuzzleStatus
import quoi.api.skyblock.dungeon.odonscanning.ScanUtils
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.Location
import quoi.module.impl.dungeon.LeapMenu
import quoi.module.impl.render.clickgui.ClickGui
import quoi.utils.Shortcuts
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.equalsOneOf
import quoi.utils.romanToInt
import quoi.utils.skyblock.PartyUtils
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/OdinFabric/blob/main/src/main/kotlin/com/odtheking/odin/utils/skyblock/dungeon/DungeonUtils.kt
 *           https://github.com/odtheking/OdinFabric/blob/main/src/main/kotlin/com/odtheking/odin/utils/skyblock/dungeon/DungeonListener.kt
 */
@Init
@Suppress("unused")
object Dungeon : EventListener, Shortcuts {

    inline val inDungeons: Boolean
        get() = Location.currentArea.isArea(Island.Dungeon)

    inline val inClear: Boolean
        get() = inDungeons && inGame && !inBoss

    var floor: Floor? = null
        private set

    inline val inBoss: Boolean
        get() = inGame && getBoss()

    var inTerminal: Boolean = false
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

    var dungeonTeammates: ArrayList<DungeonPlayer> = ArrayList(5)
        private set

    var dungeonTeammatesNoSelf: List<DungeonPlayer> = ArrayList(4)
        private set

    val allTeammates: List<String>
        get() = (dungeonTeammates.map { it.name } + PartyUtils.members).distinct()

    val allTeammatesNoSelf: List<String>
        get() = allTeammates.filter { it != player.name.string }

    var leapTeammates: List<DungeonPlayer> = ArrayList(4)
        private set

    inline val currentDungeonPlayer: DungeonPlayer
        get() = dungeonTeammates.find { it.name == player.name.string } ?:
        DungeonPlayer(player.name.string, DungeonClass.Unknown, 0, null)

    inline val isDead: Boolean
        get() = currentDungeonPlayer.isDead

    inline val doorOpener: String
        get() = dungeonStats.doorOpener

    inline val mimicKilled: Boolean
        get() = dungeonStats.mimicKilled

    inline val princeKilled: Boolean
        get() = dungeonStats.princeKilled

    inline val batKilled: Boolean
        get() = dungeonStats.batKilled

    inline val currentRoom: OdonRoom?
        get() = ScanUtils.currentRoom

    var isPaul: Boolean = false
        private set

    inline val getBonusScore: Int
        get() {
            var score = cryptCount.coerceAtMost(5)
            if (mimicKilled) score += 2
            if (princeKilled) score += 1
            if (batKilled) score += 1
            return score
        }

    inline val bloodDone: Boolean
        get() = dungeonStats.bloodDone

    inline val bloodOpen: Boolean
        get() = dungeonStats.bloodOpen

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

    inline val warpCooldown: Long
        get() = (enterTime - System.currentTimeMillis()).coerceAtLeast(0L)

    var enterTime = 0L
        private set

    var dungeonStats = DungeonStats()
        private set

    // ticks till death tick
    var deathTick = -1
        private set

    private var expectingBloodUpdate = false

    init {
        ScanUtils.init()

        on<WorldEvent.Change> {
            Blessing.entries.forEach { it.reset() }
            dungeonTeammatesNoSelf = emptyList()
            dungeonStats = DungeonStats()
            expectingBloodUpdate = false
            leapTeammates = emptyList()
            dungeonTeammates.clear()
            puzzles.clear()
            floor = if (ClickGui.forceDungeons) ClickGui.dungeonFloor.selected
            else if (Location.onZapto) Floor.F7
            else null
            isPaul = false
            deathTick = -1
        }

        on<PacketEvent.Received> {
            with(packet) {
                when (this) {
                    is ClientboundPlayerInfoUpdatePacket -> {
                        if (actions().none {
                                it.equalsOneOf(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                                    ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)
                            }) return@on
                        val tabListEntries = entries()
                            .mapNotNull { it.displayName }
                            .ifEmpty { return@on }

                        val stringEntries = tabListEntries.map { it.string }
                        val colouredEntries = tabListEntries.map { it.string to Colour.RGB(it.siblings.lastOrNull()?.style?.color?.value ?: Colour.WHITE.rgb).withAlpha(1.0f) }

                        updateDungeonTeammates(colouredEntries)
                        updateDungeonStats(stringEntries)
                        getDungeonPuzzles(stringEntries)
                    }

                    is ClientboundSetPlayerTeamPacket -> {
                        val team = parameters.orElse(null) ?: return@on

                        val text = team.playerPrefix.string.noControlCodes + team.playerSuffix.string.noControlCodes

                        floorRegex.find(text)?.groupValues?.get(1)?.let {
                            scope.launch(Dispatchers.IO) { isPaul = false /*hasBonusPaulScore()*/ } // fixme
                            val detectedFloor = Floor.valueOf(it)
                            if (floor != detectedFloor) {
                                floor = detectedFloor
                                DungeonEvent.Enter(detectedFloor).post()
                            }
                        }

                        clearedRegex.find(text)?.groupValues?.get(1)?.toIntOrNull()?.let {
                            if (dungeonStats.percentCleared != it && expectingBloodUpdate) dungeonStats.bloodDone = true
                            dungeonStats.percentCleared = it
                        }
                        dungeonTeammates.find { it.name == player.name.string }?.apply {
                            isDead = player.inventory.getItem(0).displayName.string.contains("Haunt")
                        }
                    }

                    is ClientboundTabListPacket -> {
                        Blessing.entries.forEach { blessing ->
                            blessing.regex.find(footer.string)
                                ?.let { blessing.current = romanToInt(it.groupValues[1]) }
                        }
                    }

                    is ClientboundSystemChatPacket -> {
                        val message = content.string.noControlCodes
                        if (expectingBloodRegex.matches(message)) expectingBloodUpdate = true
                        if (enterRegex.matches(message) && warpCooldown == 0L) enterTime = System.currentTimeMillis() + 30_000L
                        doorOpenRegex.matchEntire(message)?.let { match ->
                            val opener = match.groupValues[1]
                            dungeonStats.doorOpener = opener
                            DungeonEvent.DoorOpen(opener).post()
                        }
                        deathRegex.find(message)?.let { match ->
                            dungeonTeammates.find { teammate ->
                                teammate.name == (match.groupValues[1].takeUnless { it == "You" } ?: player.name.string)
                            }?.deaths?.inc()
                        }

                        when (message) {
                            "[NPC] Mort: Here, I found this map when I first entered the dungeon." -> DungeonEvent.Start().post()
                            "The BLOOD DOOR has been opened!" -> dungeonStats.bloodOpen = true
                        }

                        when (partyMessageRegex.find(message)?.groupValues?.get(1)?.lowercase() ?: return@on) {
                            "mimic killed", "mimic slain", "mimic killed!",
                            "mimic dead", "mimic dead!", $$"$skytils-dungeon-score-mimic$" -> // rip Skytils
                                dungeonStats.mimicKilled = true

                            "prince killed", "prince slain", "prince killed!",
                            "prince dead", "prince dead!", $$"$skytils-dungeon-score-prince$" -> // rip Skytils
                                dungeonStats.princeKilled = true

                            "bat killed", "bat slain", "bat killed!",
                            "bat dead", "bat dead!", $$"$skytils-dungeon-score-bat$" -> // rip Skytils
                                dungeonStats.batKilled = true

                            "blaze done!", "blaze done", "blaze puzzle solved!" ->
                                puzzles.find { it == Puzzle.BLAZE }.let { it?.status = PuzzleStatus.Completed }
                        }
                    }

                    is ClientboundOpenScreenPacket -> inTerminal = terminalTitles.any { title.string.contains(it) }
                    is ClientboundContainerClosePacket -> inTerminal = false

                    is ClientboundPingPacket -> {
                        if (id >= 0) return@on
                        if (!inClear) return@on
                        if (deathTick == 0) deathTick = 40
                        if (deathTick >= 0) deathTick--
                    }

                    is ClientboundSetTimePacket -> {
                        if (!inClear) return@on
                        val gameTime = level.gameTime
                        deathTick = 40 - (gameTime % 40).toInt()
                    }
                }
            }
        }

        on<PacketEvent.Sent> {
            when (packet) {
                is ServerboundContainerClosePacket -> inTerminal = false
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
                ?: run {
                    val player = connection.getPlayerInfo(name) ?: continue
                    previousTeammates.add(
                        DungeonPlayer(
                            name = name,
                            clazz = DungeonClass.entries.find { it.name == clazz } ?: continue,
                            clazzLvl = romanToInt(clazzLevel),
                            playerSkin = player.skin,
                            colour = colour
                        )
                    )
                }
        }
        return previousTeammates
    }

    private val WITHER_ESSENCE_IDS = UUID.fromString("2865274b-3097-394e-8149-ec629c72d850")
    private val REDSTONE_KEY = UUID.fromString("fed95410-aba1-39df-9b95-1d4f361eb66e")

    fun isWitherEssence(id: UUID?): Boolean = id == WITHER_ESSENCE_IDS
    fun isRedstoneKey(id: UUID?): Boolean = id == REDSTONE_KEY

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
                (level.getBlockEntity(pos) as? SkullBlockEntity)?.ownerProfile?.partialProfile()?.id
                    ?.let { isWitherEssence(it) || isRedstoneKey(it) } ?: false

            else -> false
        }
    }

    fun isProtectedBlock(pos: BlockPos): Boolean {
        if (level.getBlockEntity(pos) != null) return true

        val state = level.getBlockState(pos)

        return state.block in blacklistedDBBlocks
    }

    fun getBoss(): Boolean = with(player) {
        when (floor?.floorNumber) {
            1 -> x > -71 && z > -39
            in 2..4 -> x > -39 && z > -39
            in 5..6 -> x > -39 && z > -7
            7 -> x > -7 && z > -7
            else -> false
        }
    }

    fun setFloor(floor: Floor) {
        this.floor = floor
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
        dungeonTeammatesNoSelf = dungeonTeammates.filter { it.name != player.name.string }

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

    val terminalTitles = setOf("Correct all the panes!", "Change all to same color!", "Click in order!", "What starts with:", "Select all the", "Click the button on time!")

    val BLOOD_START_REGEX = Regex("^\\[BOSS] The Watcher: (Congratulations, you made it through the Entrance\\.|Ah, you've finally arrived\\.|Ah, we meet again\\.\\.\\.|So you made it this far\\.\\.\\. interesting\\.|You've managed to scratch and claw your way here, eh\\?|I'm starting to get tired of seeing you around here\\.\\.\\.|Oh\\.\\. hello\\?|Things feel a little more roomy now, eh\\?)$")

    private val enterRegex = Regex("^-*\\n\\[[^]]+] (\\w+) entered (?:MM )?\\w+ Catacombs, Floor (\\w+)!\\n-*$")
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
        var batKilled: Boolean = false,
        var doorOpener: String = "Unknown",
        var bloodDone: Boolean = false,
        var bloodOpen: Boolean = false,
        var puzzleCount: Int = 0,
    )
}