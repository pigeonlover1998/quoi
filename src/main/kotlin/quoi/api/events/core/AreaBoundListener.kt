package quoi.api.events.core

import quoi.api.skyblock.location.Area
import quoi.api.skyblock.location.Location

/**
 * [EventListener] that should only handle events when
 * specific [Area] or [Location.subarea] conditions are met
 */
interface AreaBoundListener : EventListener {
    val area: Area?
    val subarea: String?

    fun inArea(): Boolean = area?.inBase() ?: true

    fun inSubarea(): Boolean = subarea?.let { Location.subarea?.contains(it, true) == true } ?: true

    fun inEnvironment(): Boolean = (area?.inArea() ?: true) && inSubarea()

    override fun shouldHandle(event: Event): Boolean = when (event) {
        is UnfilteredEvent -> inArea() && inSubarea()
        else -> inEnvironment()
    }
}