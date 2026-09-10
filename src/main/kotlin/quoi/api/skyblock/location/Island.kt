package quoi.api.skyblock.location

import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Floor7
import quoi.api.skyblock.dungeon.enums.Phase
import quoi.api.skyblock.dungeon.enums.Stage
import quoi.module.impl.render.clickgui.ClickGui

enum class Island(val displayName: String, val command: String? = null) : Area {
    // personal
    PrivateIsland("Private Island", /*"home"*/),
    Garden("Garden", "garden"),

    // combat
    TheEnd("The End", "end"),
    SpiderDen("Spider's Den", "spider"),
    CrimsonIsle("Crimson Isle", "crimson"),
    FarmingIsland("The Farming Islands"),

    // instanced
    Dungeon("Catacombs"),
    DungeonHub("Dungeon Hub", "dh"),
    Kuudra("Kuudra"),

    // hubs
    Hub("Hub", /*"hub"*/),
    DarkAuction("Dark Auction"),
    JerryWorkshop("Jerry's Workshop"),
    Rift("The Rift"),

    // foraging/modern
    ThePark("The Park", "park"),
    BackwaterBayou("Backwater Bayou"),
    Galatea("Galatea", "galatea"),
    LotusAtoll("Lotus Atoll"),

    // mining
    GoldMine("Gold Mine", "gold"),
    DeepCaverns("Deep Caverns", "deep"),
    DwarvenMines("Dwarven Mines", "mines"),
    CrystalHollows("Crystal Hollows", "ch"),
    Mineshaft("Mineshaft"),
    Mining("Mining") {
        override fun inBase(): Boolean = Location.currentArea.isArea(
            GoldMine, DeepCaverns, DwarvenMines, CrystalHollows, Mineshaft
        )
    },

    // global
    Skyblock("Skyblock") {
        override fun inBase(): Boolean = Location.inSkyblock || ClickGui.forceSkyblock
    },

    // other
    SinglePlayer("Singleplayer"),
    Unknown("(Unknown)");

    override fun inBase(): Boolean {
        if (Location.currentArea == SinglePlayer) return true
        if (this == Dungeon && (ClickGui.forceDungeons || Location.onZapto)) return true
        return Location.currentArea == this
    }

    override fun inActive(): Boolean = true

    fun isArea(area: Island): Boolean {
        if (this == SinglePlayer) return true
        if (area == Dungeon && (ClickGui.forceDungeons || Location.onZapto)) return true
        return this == area
    }

    fun isArea(vararg areas: Island): Boolean {
        if (this == SinglePlayer) return true
        return this in areas
    }
}

/**
 * dungeon area condition with optional floor, clear, or boss filters.
 *
 * created via [Island.invoke]
 * example:
 *  `Island.Dungeon(floor = 7, inClear = true)`
 *  in this case [Area.inActive] will be true only
 *  when [Dungeon.floor] number is `7` and [Dungeon.inClear] is `true`
 */
data class DungeonInstance(
    val floor: Int? = null,
    val inClear: Boolean? = null,
    val inBoss: Boolean? = null,
    val phase: Phase? = null,
    val stage: Stage? = null
) : Area {
    override fun inBase(): Boolean = Island.Dungeon.inBase()

    override fun inActive(): Boolean {
        floor?.let { if (Dungeon.floor?.floorNumber != it) return false }
        inClear?.let { if (it && !Dungeon.inClear) return false }
        inBoss?.let { if (it && !Dungeon.inBoss) return false }
        phase?.let { if (!Floor7.inPhase(it)) return false }
        stage?.let { if (!Floor7.inStage(it)) return false }
        return true
    }
}

/**
 * operator for the `Island.Dungeon(..)` syntax.
 *
 * - [Island.Dungeon] returns [DungeonInstance] with specified filters
 * - all other islands return [Island] enum entry itself
 *
 * ## extending
 * to add conditional areas for other islands (e.g. kuudra tier or phases),
 * add nullable params here and create a corresponding data class implementing [Area]
 */
operator fun Island.invoke(
    floor: Int? = null,
    inClear: Boolean? = null,
    inBoss: Boolean? = null,
    phase: Phase? = null,
    stage: Stage? = null
): Area = when (this) {
    Island.Dungeon -> DungeonInstance(floor, inClear, inBoss, phase, stage)
    else -> this
}