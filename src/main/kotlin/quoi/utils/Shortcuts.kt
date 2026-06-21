package quoi.utils

import quoi.QuoiMod

interface Shortcuts {
    val mc get() = QuoiMod.mc
    val player get() = requireNotNull(mc.player) { "tried to access player before it was loaded" }
    val level get() = requireNotNull(mc.level) { "tried to access level before world was loaded" }
}