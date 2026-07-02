package quoi.api.skyblock.location

import quoi.api.events.core.UnfilteredEvent

/**
 * represents a checkable area conditions
 *
 * decouples main [Island] location from active state conditions.
 * simple locations (like [Island.Hub]) implement this directly via the [Island] enum,
 * while complex states (like specific dungeon floors) use dedicated data classes
 * created via the [invoke] operator.
 */
interface Area {

    /**
     * checks if the player is in the correct base location.
     * used primarily for [UnfilteredEvent] filtering
     * where we want to process events in the general area regardless of stat.e
     */
    fun inBase(): Boolean

    /**
     * checks if the specific active state conditions are met
     * for simple islands this always returns `true`, for complex areas
     * like [DungeonInstance] this checks floor, clear, or boss states
     */
    fun inActive(): Boolean

    /**
     * combined [inBase] and [inArea] check
     */
    fun inArea(): Boolean = inBase() && inActive()
}