package quoi.module.impl.misc.slayers

import quoi.module.Module
import quoi.module.impl.misc.slayers.blaze.BlazeSlayer

@Suppress("unused_expression")
object Slayers : Module(
    "Slayers"
) {
    init {
        BlazeSlayer
    }
}