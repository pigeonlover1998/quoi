package quoi.annotations

@RequiresOptIn(message = "Internal shit")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class Internal