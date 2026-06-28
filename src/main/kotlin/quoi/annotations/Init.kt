package quoi.annotations

import quoi.api.events.core.Priority

/**
 * Marks a class or object for automatic initialisation
 *
 * @property priority the initialisation order priority (see [Priority]). higher values run first
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Init(val priority: Int = 0)