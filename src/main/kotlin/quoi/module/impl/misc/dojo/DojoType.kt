package quoi.module.impl.misc.dojo

enum class DojoType {
    FORCE,
    STAMINA,
    MASTERY,
    DISCIPLINE,
    SWIFTNESS,
    CONTROL,
    TENACITY,
    NONE;

    companion object {
        fun fromString(string: String): DojoType =
            entries.firstOrNull { string.contains(it.name) } ?: NONE
    }
}