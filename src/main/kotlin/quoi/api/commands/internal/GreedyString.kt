package quoi.api.commands.internal

class GreedyString(val value: String) {
    val string: String get() = value
    override fun toString(): String = value
}