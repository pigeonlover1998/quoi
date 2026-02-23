package quoi.module.impl.mining

import quoi.QuoiMod.MOD_ID
import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.dsl.size
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.Island
import quoi.module.Module
import quoi.module.impl.mining.CrystalHollowsScanner.foundRouteBlocks
import quoi.module.impl.mining.CrystalHollowsScanner.routeScanner
import quoi.module.impl.mining.CrystalHollowsScanner.scannedChunks
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.ColourSetting
import quoi.module.settings.impl.DropdownSetting
import quoi.module.settings.impl.NumberSetting
import quoi.utils.EntityUtils.playerEntities
import quoi.utils.StringUtils.width
import quoi.utils.WorldUtils
import quoi.utils.WorldUtils.worldToMap
import quoi.utils.rad
import quoi.utils.render.DrawContextUtils.drawImage
import quoi.utils.render.DrawContextUtils.drawPlayerHead
import quoi.utils.render.DrawContextUtils.drawString
import quoi.utils.render.DrawContextUtils.rect
import quoi.utils.render.DrawContextUtils.withMatrix
import quoi.utils.ui.hud.Hud
import quoi.utils.ui.hud.setting
import quoi.utils.ui.hud.withTransform
import quoi.utils.ui.rendering.NVGRenderer.image
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import java.util.*
import quoi.module.impl.mining.CrystalHollowsScanner.enabled as chScanner

object CrystalHollowsMap : Module(
    "Crystal Hollows Map",
    area = Island.CrystalHollows
) {
    private val iconScale by NumberSetting("Icon scale", 2.0f, 0.1f, 5.0f, 0.1f)
    private val drawPlayers by BooleanSetting("Draw players")
    private val onlyGriefed by BooleanSetting("Only griefed").withDependency { drawPlayers && GrieferTracker.enabled }
    private val drawOutOfRange by BooleanSetting("Draw out of range").withDependency { drawPlayers }
    private val drawNames by BooleanSetting("Draw names").withDependency { drawPlayers }
    private val textScale by NumberSetting("Text scale", 2.0f, 0.1f, 5.0f, 0.1f).withDependency { drawPlayers && drawNames }

    private val other by DropdownSetting("Other").collapsible()
    private val drawChunks by BooleanSetting("Draw loaded chunks").withDependency(other) { chScanner }
    private val chunksCol by ColourSetting("Loaded chunks colour", Colour.PURPLE.withAlpha(0.33f), allowAlpha = true).withDependency(other) { chScanner && drawChunks }
    private val drawRouteBlocks by BooleanSetting("Draw route blocks").withDependency(other) { chScanner && routeScanner }

    private val hollowsMap by Hud("Hollows map", toggleable = false) { // todo add more stuff
        if (preview) image(
            "crystalhollowsmap.png".image(),
            size(MAP_SIZE.px, MAP_SIZE.px)
        )
    }.withSettings(
        ::iconScale, ::drawPlayers, ::onlyGriefed, ::drawOutOfRange, ::drawNames, ::textScale,
        ::other, ::drawChunks, ::chunksCol, ::drawRouteBlocks
    ).setting()

    const val X_MIN = 202
    const val X_MAX = 823
    const val Z_MIN = 202
    const val Z_MAX = 823
    const val MAP_SIZE = 621

    private val GREEN_MARKER = ResourceLocation.fromNamespaceAndPath(MOD_ID, "green_marker.png")
    private val WHITE_MARKER = ResourceLocation.fromNamespaceAndPath(MOD_ID, "white_marker.png")
    private val MAP_IMAGE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "crystalhollowsmap.png")

    private val Number.mapX get() = worldToMap(this, X_MIN, X_MAX, 0, MAP_SIZE).toFloat()
    private val Number.mapZ get() = worldToMap(this, Z_MIN, Z_MAX, 0, MAP_SIZE).toFloat()

    private var meshedChunks = listOf<MeshedChunk>()

    var isDirty = false

    private const val GRID_SIZE = 64
    private const val CHUNK_OFFSET = X_MIN shr 4
    data class MeshedChunk(val x: Int, val z: Int, val width: Int, val height: Int)

    private val mapPlayers = mutableMapOf<String, MapPlayer>()

    init {
        on<WorldEvent.Change> {
            mapPlayers.clear()
        }

        on<TickEvent.End> {
            if (!drawPlayers) return@on

            mapPlayers.keys.retainAll(WorldUtils.players.map { it.profile.name }.toSet())

            val griefedPlayers = GrieferTracker.getPlayers().map { it.name }
            playerEntities.forEach {
                val name = it.name.string
                if (onlyGriefed && name !in griefedPlayers && it.uuid != player.uuid) return@forEach
                mapPlayers[name] = MapPlayer(it.uuid, name, it.x, it.z, it.yHeadRot)
            }

            val now = System.currentTimeMillis()
            mapPlayers.entries.removeIf {
                it.value.uuid != player.uuid &&
                (now - it.value.lastSeen > 60_000 || (onlyGriefed && it.value.name !in griefedPlayers))
            }
        }

        on<RenderEvent.Overlay> {
            hollowsMap.withTransform(ctx) {
                ctx.renderMap()
            }
        }
    }

    private fun GuiGraphics.renderMap() {

        drawImage(MAP_IMAGE, 0, 0, MAP_SIZE, MAP_SIZE)

        if (drawChunks) {
            if (isDirty) rebuildMeshedChunks()

            val col = chunksCol.rgb

            meshedChunks.forEach { chunk ->
                rect(
                    x = (chunk.z shl 4).mapZ,
                    y = (chunk.x shl 4).mapX,
                    width = chunk.height,
                    height = chunk.width,
                    colour = col
                )
            }
        }

        if (drawRouteBlocks) {
            foundRouteBlocks.forEach { pos ->
                rect(
                    x = pos.x.mapX,
                    y = pos.z.mapZ,
                    width = 5,
                    height = 5,
                    colour = CrystalHollowsScanner.colour.withAlpha(1.0f).rgb
                )
            }
        }
        if (drawPlayers) drawPlayers()
    }

    private fun GuiGraphics.drawPlayers() {
        val l = playerEntities.map { it.uuid }
        mapPlayers.values.sortedBy { it.name == player.name.string }.forEach { p ->
            val inRange = l.contains(p.uuid)
            if (p.uuid != player.uuid && (drawOutOfRange || inRange)) drawName(p.name, p.x, p.z)

            withMatrix(p.x.mapX, p.z.mapZ) {
                pose().rotate((p.yHeadRot - 180f).rad)
                pose().scale(iconScale, iconScale)

                if (p.uuid == player.uuid) {
                    drawImage(GREEN_MARKER, -4, -5, 7, 10)
                } else if (inRange) {
                    rect(-6, -6, 12, 12, Colour.BLACK.rgb)
                    pose().scale(1f - 2f, 1f - 2f)
                    drawPlayerHead(p.uuid, -6, -6, 12)
                } else if (drawOutOfRange) {
                    drawImage(WHITE_MARKER, -4, -5, 7, 10)
                }
            }
        }
    }

    private fun GuiGraphics.drawName(name: String, x: Double, z: Double) {
        if (!drawNames) return
        withMatrix(x.mapX, z.mapZ, textScale) {
            pose().translate(0f, 10f)

            drawString(name, -name.width() / 2f, 0)
        }
    }

    /**
     * I'm so pro
     * MESHED: 20
     * SCANNED: 565
     *
     * https://0fps.net/2012/06/30/meshing-in-a-minecraft-game/ + some ai
     */
    private fun rebuildMeshedChunks() {
        val grid = Array(GRID_SIZE) { BooleanArray(GRID_SIZE) }

        synchronized(scannedChunks) {
            scannedChunks.forEach { chunkKey ->
                val cx = (chunkKey shr 32).toInt() - CHUNK_OFFSET
                val cz = chunkKey.toInt() - CHUNK_OFFSET
                if (cx in 0 until GRID_SIZE && cz in 0 until GRID_SIZE) {
                    grid[cx][cz] = true
                }
            }
        }

        val chunks = mutableListOf<MeshedChunk>()

        for (x in 0 until GRID_SIZE) {
            for (z in 0 until GRID_SIZE) {
                if (grid[x][z]) {
                    var width = 1
                    while (x + width < GRID_SIZE && grid[x + width][z]) {
                        width++
                    }

                    var height = 1
                    var canExpand = true
                    while (canExpand && z + height < GRID_SIZE) {
                        for (k in 0 until width) {
                            if (!grid[x + k][z + height]) {
                                canExpand = false
                                break
                            }
                        }
                        if (canExpand) height++
                    }

                    chunks.add(MeshedChunk(x + CHUNK_OFFSET, z + CHUNK_OFFSET, width shl 4, height shl 4))

                    for (dX in 0 until width) {
                        for (dZ in 0 until height) {
                            grid[x + dX][z + dZ] = false
                        }
                    }
                }
            }
        }
        meshedChunks = chunks
        isDirty = false
    }

    private data class MapPlayer(
        val uuid: UUID,
        val name: String,
        var x: Double,
        var z: Double,
        var yHeadRot: Float,
        var lastSeen: Long = System.currentTimeMillis()
    )
}