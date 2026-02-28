package quoi.api.skyblock

import quoi.QuoiMod.mc
import quoi.api.events.AreaEvent
import quoi.api.events.core.EventBus
import quoi.api.events.PacketEvent
import quoi.api.events.ServerEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventPriority
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.equalsOneOf
import quoi.utils.StringUtils.startsWithOneOf
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/OdinFabric/blob/main/src/main/kotlin/com/odtheking/odin/utils/skyblock/LocationUtils.kt
 */
object Location {
    var onHypixel: Boolean = false
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

    val onModernIsland: Boolean get() = currentArea.equalsOneOf(Island.ThePark, Island.Galatea)

    private val teamRegex = Regex("^team_(\\d+)$")
    private val subAreaRegex = Regex("^ ([⏣ф]) .*")
    private val serverIdRegex = Regex("\\d\\d/\\d\\d/\\d\\d (\\w{0,6}) *")

    fun init() {
        EventBus.on<PacketEvent.Received> {
            when (packet) {
                is ClientboundPlayerInfoUpdatePacket -> {
                    if (!currentArea.isArea(Island.Unknown) || packet.actions()
                            .none { it.equalsOneOf(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,) }
                    ) return@on
                    val area = packet.entries()?.find {
                        it?.displayName?.string?.startsWithOneOf(
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
                    if (!inSkyblock) inSkyblock = onHypixel && packet.objectiveName == "SBScoreboard"

                is ClientboundSetPlayerTeamPacket -> {
                    val team = packet.parameters?.orElse(null) ?: return@on
                    val text = team.playerPrefix?.string?.noControlCodes?.plus(team.playerSuffix?.string?.noControlCodes) ?: return@on

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

        EventBus.on<WorldEvent.Change>(EventPriority.LOW) {
            currentArea = Island.Unknown
            inSkyblock = false
            AreaEvent.Main(currentArea).post()

            if (subarea !== null) {
                AreaEvent.Sub(null).post()
                subarea = null
            }
        }

        EventBus.on<ServerEvent.Connect> {
            if (mc.isSingleplayer) {
                currentArea = Island.SinglePlayer
                return@on
            }
            onHypixel = mc.runCatching { ip.contains("hypixel", true) }.getOrDefault(false)
        }

        EventBus.on<ServerEvent.Disconnect> {
            currentArea = Island.Unknown
            subarea = null
            inSkyblock = false
            onHypixel = false
        }
    }
}