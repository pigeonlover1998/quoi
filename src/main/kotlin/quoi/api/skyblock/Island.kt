package quoi.api.skyblock

import quoi.api.skyblock.dungeon.Dungeon
import quoi.module.impl.render.ClickGui

enum class Island(val displayName: String, val command: String? = null) {
    SinglePlayer("Singleplayer"),
    PrivateIsland("Private Island", /*"home"*/),
    Garden("Garden", "garden"),
    SpiderDen("Spider's Den", "spider"),
    CrimsonIsle("Crimson Isle", "crimson"),
    TheEnd("The End", "end"),
    GoldMine("Gold Mine", "gold"),
    DeepCaverns("Deep Caverns", "deep"),
    DwarvenMines("Dwarven Mines", "mines"),
    CrystalHollows("Crystal Hollows", "ch"),
    FarmingIsland("The Farming Islands"),
    ThePark("The Park", "park"),
    Dungeon("Catacombs"),
    DungeonHub("Dungeon Hub", "dh"),
    Hub("Hub", /*"hub"*/),
    DarkAuction("Dark Auction"),
    JerryWorkshop("Jerry's Workshop"),
    Kuudra("Kuudra"),
    Mineshaft("Mineshaft"),
    Rift("The Rift"),
    BackwaterBayou("Backwater Bayou"),
    Galatea("Galatea", "galatea"),
    Unknown("(Unknown)");

    fun isArea(area: Island): Boolean {
        if (this == SinglePlayer) return true
        if (area == Dungeon && ClickGui.forceDungeons) return true
        return this == area
    }

    fun isArea(vararg areas: Island): Boolean {
        if (this == SinglePlayer) return true
        return this in areas
    }
}

sealed class IslandArea { // idkmaybe
    abstract val island: Island

    open fun inBase() = Location.currentArea.isArea(island)

    open fun inActive() = true

    fun inArea() = inBase() && inActive()

    data class Base(override val island: Island) : IslandArea()

    data class DungeonInstance(
        val floor: Int? = null,
        val inClear: Boolean? = null,
        val inBoss: Boolean? = null
    ) : IslandArea() {
        override val island = Island.Dungeon

        override fun inActive(): Boolean {
            floor?.let { if (Dungeon.floor?.floorNumber != it) return false }
            inClear?.let { if (it && !Dungeon.inClear) return false }
            inBoss?.let { if (it && !Dungeon.inBoss) return false }
            return true
        }
    }
}

operator fun Island.invoke(
    floor: Int? = null,
    inClear: Boolean? = null,
    inBoss: Boolean? = null
): IslandArea = when (this) {
    Island.Dungeon -> IslandArea.DungeonInstance(floor, inClear, inBoss)
    else -> IslandArea.Base(this)
}