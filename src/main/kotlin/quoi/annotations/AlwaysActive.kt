package quoi.annotations

import quoi.module.Module
/**
 * Keeps [Module] active ignoring its disabled state
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class AlwaysActive