package quoi.api.commands

import quoi.QuoiMod.mc
import quoi.api.commands.internal.BaseCommand
import quoi.api.commands.internal.GreedyString
import quoi.api.events.core.EventBus
import quoi.api.skyblock.Island
import quoi.api.skyblock.Location
import quoi.api.skyblock.Location.currentArea
import quoi.api.skyblock.Location.currentServer
import quoi.api.skyblock.Location.inSkyblock
import quoi.api.skyblock.Location.subarea
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Dungeon.uniqueRooms
import quoi.api.skyblock.dungeon.map.utils.ScanUtils.currentRoom
import quoi.module.ModuleManager
import quoi.module.impl.misc.Chat
import quoi.module.impl.render.ClickGui.clickGui
import quoi.utils.ChatUtils.command
import quoi.utils.ChatUtils.literal
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.scheduleLoop
import quoi.utils.WorldUtils
import quoi.utils.WorldUtils.day
import quoi.utils.skyblock.player.PlayerUtils.hold
import quoi.utils.skyblock.player.PlayerUtils.isMoving
import quoi.utils.ticker
import quoi.utils.ui.hud.HudManager
import quoi.utils.ui.screens.UIScreen.Companion.open
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.world.phys.BlockHitResult

object QuoiCommand {
    val command = BaseCommand("quoi", "requise") {
        open(clickGui)
    }

    val devCommand = BaseCommand("quoidev")

    private fun warpTicker(cmd: String) = ticker {
        action { command("warp $cmd") }
        action(80) { command("warp hub") }
        delay(80)
    }

    private fun antiAfkTicker(delay: Int) = ticker {
        action { mc.options.keyLeft.hold(1) }
        action(delay) { mc.options.keyRight.hold(1) }
        delay(delay)
    }

    init {

        with(devCommand) {
            "simulate" { message: GreedyString ->
                EventBus.onPacketReceived(ClientboundSystemChatPacket(literal(message.string), false))
                modMessage("simulated: ${message.string}")
            }

            "currentroom" {
                currentRoom?.let {
                    modMessage("Current room: ${it.name}")
                    modMessage("DATA: CORNER: ${it.corner} ROTATION: ${it.rotation} ")
                }
            }

            "relative" {
                mc.hitResult?.let {
                    if (it !is BlockHitResult) return@let
                    currentRoom?.getRelativeCoords(it.blockPos)?.let { vec2 ->
                        modMessage("Relative coords: ${vec2.x}, ${vec2.z}")
                    }

                }
            }

            "rooms" {
                modMessage("Rooms: ${uniqueRooms.joinToString(", ") { it.name }}")
            }

            "area" {
                modMessage("Area: $currentArea, Sub: $subarea, Server: $currentServer, Floor: ${Dungeon.floor?.name}")
            }
        }

        with(command) {
            "toggle" { moduleName: GreedyString ->
                val module = ModuleManager.getModuleByName(moduleName.string)
                module?.apply {
                    toggle()
                    toggleMessage()
                } ?: modMessage("Unknown module name: ${moduleName.string}")
            }.suggests { ModuleManager.modules.map { it.name } }.description("Toggles specified module.")

            "hud" { open(HudManager.editor()) }.description("Opens Hud editor.")
        }

        command.sub("findlobby") { area: String, criteria: String, value: String ->
            val island = Island.entries
                .firstOrNull { it.command != null && it.displayName.equals(area.replace("_", " "), true) }
                ?: return@sub modMessage("&cIncorrect area!")

            if (criteria !in setOf("day", "server", "player")) return@sub modMessage("&cInvalid criteria!")

            val intValue = if (criteria == "day") value.toIntOrNull()
                ?: return@sub modMessage("&cInvalid day number!") else null

            fun isMet(): Boolean = when (criteria) {
                "day" -> mc.level!!.day <= intValue!!
                "server" -> Location.currentServer.equals(value, true)
                "player" -> WorldUtils.players.any { it.profile.name.equals(value, true) }
                else -> false
            }

            var ticker = warpTicker(island.command!!)

            modMessage("Starting to look for $criteria $value")

            scheduleLoop {
                if (mc.player!!.isMoving) {
                    modMessage("Cancelling, you moved!")
                    it.cancel()
                    return@scheduleLoop
                }

                if (isMet() && currentArea.isArea(island)) {
                    modMessage("Found")
                    it.cancel()
                    return@scheduleLoop
                }

                if (ticker.tick()) ticker = warpTicker(island.command)
            }
        }.description("Finds lobby with specified criteria.")
        .requires("&cYou are not in skyblock!") { inSkyblock }
        .suggests("area") { Island.entries.filter { it.command != null }.map { it.displayName.replace(" ", "_") } }
        .suggests("criteria", "day", "server", "player")

        command.sub("antiafk") { delay: Int ->
            if (delay < 20) return@sub modMessage("&cThe delay is too low!")
            val headRot = mc.player!!.yHeadRot
            modMessage("Starting. Move your camera to cancel")

            var ticker = antiAfkTicker(delay)
            scheduleLoop {
                if (mc.player!!.yHeadRot != headRot) {
                    modMessage("Cancelling, you moved your camera!")
                    it.cancel()
                    return@scheduleLoop
                }

                if (ticker.tick()) ticker = antiAfkTicker(delay)
            }
        }.description("Prevents afk kick.").suggests("delay", "40")
    }

    fun initialise() {
        command.register()
        devCommand.register()

        BaseCommand("clearchat") { mc.gui.chat.clearMessages(false); Chat.chatList.clear() }.register()

        Floors.entries.forEach { floor ->
            BaseCommand(floor.name.lowercase()) {
                command("joininstance ${floor.instance()}")
            }.requires("&cYou are not in skyblock!") { inSkyblock }.register()
        }
    }

    private enum class Floors {
        F0,
        F1, F2, F3, F4, F5, F6, F7,
        M1, M2, M3, M4, M5, M6, M7;

        private val floors = listOf("one", "two", "three", "four", "five", "six", "seven")

        fun instance(): String {
            if (this == F0) return "catacombs_entrance"

            val adj = ordinal - 1

            return "${if (adj > 6) "master_" else ""}catacombs_floor_${floors[adj % 7]}"
        }
    }
}