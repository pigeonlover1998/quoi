package quoi.annotations

/**
 * Prevents accidental use inside the project.
 * Other classes or objects must opt in to use this.
 */
@RequiresOptIn
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class Internal