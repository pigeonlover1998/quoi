package quoi.api.abobaui.operations

// functions that are run every frame
fun interface Operation {
    fun run(): Boolean
}