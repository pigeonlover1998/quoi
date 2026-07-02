package quoi.api.skyblock.location

import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
import quoi.QuoiMod.mc
import quoi.annotations.Init
import quoi.api.events.AreaEvent
import quoi.api.events.PacketEvent
import quoi.api.events.ServerEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.module.impl.render.clickgui.ClickGui
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.StringUtils.startsWithOneOf
import quoi.utils.equalsOneOf

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/OdinFabric/blob/main/src/main/kotlin/com/odtheking/odin/utils/skyblock/LocationUtils.kt
 */
@Init
object Location : EventListener {
    var onHypixel: Boolean = false
        private set
    var onZapto: Boolean = false
        private set
    var inSkyblock: Boolean = false
        private set
    var currentArea: Island = Island.Unknown
        private set
    var subarea: String? = null
        private set
    var currentServer: String? = null
        private set
    var previousServer: String? = null
        private set

    private val teamRegex = Regex("^team_(\\d+)$")
    private val subAreaRegex = Regex("^ ([⏣ф]) .*")
    private val serverIdRegex = Regex("\\d\\d/\\d\\d/\\d\\d (\\w{0,6}) *")

    init {
        on<PacketEvent.Received> {
            when (packet) {
                is ClientboundPlayerInfoUpdatePacket -> {
                    if (!currentArea.isArea(Island.Unknown) || packet.actions()
                            .none { it.equalsOneOf(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME) }
                    ) return@on
                    val area = packet.entries().find {
                        it.displayName?.string?.startsWithOneOf(
                            "Area: ",
                            "Dungeon: "
                        ) == true
                    }?.displayName?.string ?: return@on
                    val newArea = Island.entries.firstOrNull { area.contains(it.displayName, true) } ?: Island.Unknown
                    if (newArea !== currentArea) {
                        currentArea = newArea
                        AreaEvent.Main(newArea).post()
                    }
                }

                is ClientboundSetObjectivePacket ->
                    if (!inSkyblock) inSkyblock = onHypixel && packet.objectiveName == "SBScoreboard" || ClickGui.forceSkyblock || onZapto

                is ClientboundSetPlayerTeamPacket -> {
                    val team = packet.parameters.orElse(null) ?: return@on
                    val text = team.playerPrefix.string.noControlCodes + team.playerSuffix.string.noControlCodes

                    if (packet.name.matches(teamRegex) && text.matches(subAreaRegex) && text.lowercase() != subarea) {
                        subarea = text.lowercase()
                        AreaEvent.Sub(text).post()
                    }

                if (currentArea == Island.Unknown) serverIdRegex.find(text)?.groupValues?.getOrNull(1)?.let {
                        if (currentServer != it) {
                            previousServer = currentServer
                            currentServer = it
                        }
                    }
                }
            }
        }

        on<WorldEvent.Change>(Priority.LOW) {
            currentArea = Island.Unknown
            inSkyblock = ClickGui.forceSkyblock
            AreaEvent.Main(currentArea).post()

            if (subarea !== null) {
                AreaEvent.Sub(null).post()
                subarea = null
            }
        }

        on<ServerEvent.Connect> {
            if (mc.isSingleplayer) {
                currentArea = Island.SinglePlayer
                return@on
            }
            onZapto = mc.runCatching { ip.contains("p3sim", true) }.getOrDefault(false)
            onHypixel = mc.runCatching { ip.contains("hypixel", true) }.getOrDefault(false) || onZapto
        }

        on<ServerEvent.Disconnect> {
            currentArea = Island.Unknown
            subarea = null
            inSkyblock = false
            onHypixel = false
        }
    }
}