package quoi.utils

import quoi.QuoiMod

interface Shortcuts {
    val Shortcuts.mc get() = QuoiMod.mc
    val Shortcuts.player get() = requireNotNull(mc.player) { "tried to access player before it was loaded" }
    val Shortcuts.level get() = requireNotNull(mc.level) { "tried to access level before world was loaded" }
    val Shortcuts.connection get() = requireNotNull(mc.connection) { "mc.connection is null" }
    val Shortcuts.gameMode get() = requireNotNull(mc.gameMode) { "mc.gameMode is null" }
    val Shortcuts.inGame get() = mc.level != null && mc.player != null
}