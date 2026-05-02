package quoi.api.skyblock.dungeon.odonscanning

import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes
import quoi.QuoiMod.MOD_ID
import quoi.QuoiMod.mc
import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.at
import quoi.api.abobaui.dsl.constrain
import quoi.api.abobaui.dsl.minus
import quoi.api.abobaui.dsl.onClick
import quoi.api.abobaui.dsl.onMouseEnterExit
import quoi.api.abobaui.dsl.percent
import quoi.api.abobaui.dsl.plus
import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.dsl.radius
import quoi.api.abobaui.dsl.seconds
import quoi.api.abobaui.dsl.size
import quoi.api.abobaui.dsl.withScale
import quoi.api.abobaui.elements.Element
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.abobaui.elements.impl.refreshableGroup
import quoi.api.animations.Animation
import quoi.api.colour.Colour
import quoi.api.colour.colour
import quoi.api.colour.mix
import quoi.api.colour.multiply
import quoi.api.colour.withAlpha
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Dungeon.floor
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonDoor
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomType
import quoi.module.impl.dungeon.InteractiveMap
import quoi.module.impl.dungeon.DungeonMap
import quoi.utils.StringUtils.width
import quoi.api.vec.Vec2i
import quoi.utils.equalsOneOf
import quoi.utils.rad
import quoi.utils.render.DrawContextUtils.drawImage
import quoi.utils.render.DrawContextUtils.rect
import quoi.utils.skyblock.ItemUtils.skyblockId
import quoi.utils.ui.rendering.Font
import quoi.utils.ui.rendering.NVGRenderer
import quoi.utils.ui.rendering.NVGRenderer.defaultFont
import quoi.utils.ui.watch
import kotlin.jvm.optionals.getOrNull

object MapRenderer {

    private var startCoords: Vec2i? = null
    var mapSize: Vec2i = Vec2i(0, 0)
    var roomSize: Int? = null
        private set
    var mapCentre: Vec2i = Vec2i(0, 0)
        private set
    var refreshShit = 0
        private set

    private val WHITE_MARKER = ResourceLocation.fromNamespaceAndPath(MOD_ID, "white_marker.png")
    private val GREEN_MARKER = ResourceLocation.fromNamespaceAndPath(MOD_ID, "green_marker.png")

    fun refresh() {
        refreshShit++
    }

    fun ElementScope<*>.renderMap(config: MapConfig): ElementScope<*> {
        val (scale, radius) = config

        val thickness = (12 * scale).px
        val size = 116f * scale

        return block(
            size(size.px + thickness, size.px + thickness),
            colour = DungeonMap.bgColour,
            (radius * 1.5f).radius()
        ) {
            if (DungeonMap.border) outline(DungeonMap.borderColour, thickness = DungeonMap.borderThickness.px)

            val refreshable = refreshableGroup(constrain(Centre, Centre, Copying - thickness, Copying - thickness)) {

                val baseW = (mapSize.x * 16 + (mapSize.x - 1) * 4).toFloat().coerceAtLeast(1f)
                val baseH = (mapSize.z * 16 + (mapSize.z - 1) * 4).toFloat().coerceAtLeast(1f)

                val newScale = size / maxOf(baseW, baseH)

                val new = config.copy(
                    scale = newScale,
                    fontScale = config.fontScale * (newScale / scale),
                    radius = config.radius * (newScale / scale),
                    icon = config.icon.copy(
                        scale = config.icon.scale * (newScale / scale),
                        nameScale = config.icon.nameScale * (newScale / scale),
                        thickness = maxOf(1, (config.icon.thickness * (newScale / scale)).toInt())
                    )
                )

                val dynamicW = baseW * newScale
                val dynamicH = baseH * newScale

                group(constrain(Centre, Centre, dynamicW.px, dynamicH.px)) {
                    ScanUtils.scannedDoors.forEach { door ->
                        val pos = door.placement
                        val size = door.size

                        val col = Colour.Animated(
                            from = colour { door.colour.rgb },
                            to = colour { door.colour.rgb.multiply(1.15f) }
                        )

                        val outlineCol = Colour.Animated(
                            from = Colour.TRANSPARENT,
                            to = Colour.WHITE.withAlpha(180)
                        )

                        block(
                            constrain(
                                x = (pos.x * newScale).px,
                                y = (pos.z * newScale).px,
                                w = (size.x * newScale).px,
                                h = (size.z * newScale).px
                            ),
                            colour = col
                        ) {
                            outline(outlineCol, thickness = 2.px)

                            onClick {
                                InteractiveMap.getDoorPath(door)
                            }

                            onMouseEnterExit {
                                col.animate(0.15.seconds, style = Animation.Style.EaseOutQuint)
                                outlineCol.animate(0.15.seconds, style = Animation.Style.EaseOutQuint)
                            }
                        }
                    }

                    ScanUtils.scannedRooms.forEach { room ->
                        renderTiles(new, room)
                        renderName(new, room)
                    }
//                    if (config.icons) renderIcons(new, dynamicW, dynamicH) // todo
                }
            }

            watch({ refreshShit }) {
                refreshable.refresh()
            }
        }
    }

    private fun ElementScope<*>.renderIcons(config: MapConfig, width: Float, height: Float) {
        val (mapScale, _, font) = config
        val icon = config.icon
        val (scale, heads, ownHead, border, borderColour, classColour, thickness, name, whenLeap, nameScale) = icon
//        ui.debug = true
        watch({ Dungeon.dungeonTeammates.size }) {
            refresh()
        }

        Dungeon.dungeonTeammates.forEach { player ->
            val self = player.name == mc.player?.name?.string
            val useHead = heads && (!self || ownHead)

            val (w0, h0) = if (useHead) 20f to 20f else 14f to 20f

            val w = w0 * scale
            val h = h0 * scale
            val t = if (border && useHead) thickness.toFloat() * 2 else 0f

            val x = object : Constraint.Position {
                override fun calculatePos(element: Element, horizontal: Boolean): Float {
                    val pos = player.position().first * mapScale - (w + t) // / 2f
                    return pos.coerceIn(0f, width - (w + t) + w - 1)
                }
            }
            val z = object : Constraint.Position {
                override fun calculatePos(element: Element, horizontal: Boolean): Float {
                    val pos = player.position().second * mapScale - (h + t) // / 2f
                    return pos.coerceIn(0f, height - (h + t) + h - 1)
                }
            }

            column(constrain(x, z, Bounding, Bounding)) {
                object : Element(size(w.px, h.px)) {
                    init { usingCtx = true }

                    override fun drawCtx() {
                        if (player.isDead) return
                        parent?.redraw()
                        val w = w.toInt()
                        val h = h.toInt()

                        withScale {
                            ctx.pose().translate(w / 2f, h / 2f)
                            ctx.pose().rotate((180.0 + player.yaw()).rad)
                            ctx.pose().translate(-w / 2f, -h / 2f)
                            when {
                                self && !useHead -> ctx.drawImage(GREEN_MARKER, 0, 0, w, h)
                                useHead -> player.playerSkin?.let {
                                    val col = if (classColour) player.clazz.colour else borderColour

                                    if (border) {
                                        ctx.rect(w - t / 2, h - t / 2, w + t.toInt(), h + t.toInt(), col.rgb)
                                    }
                                    PlayerFaceRenderer.draw(ctx, it, w, h, w)
                                } ?: ctx.drawImage(WHITE_MARKER, 0, 0, w, h)
                                else -> ctx.drawImage(WHITE_MARKER, 0, 0, w, h)
                            }
                        }
                    }
                }.add()

                if (name && !self) textSupplied(
                    supplier = {
                        val holdingLeap = mc.player?.mainHandItem?.skyblockId?.equalsOneOf("INFINITE_SPIRIT_LEAP", "SPIRIT_LEAP") == true
                        if (!whenLeap || holdingLeap) player.name else "  "
                    },
                    font = font,
                    size = (18f * nameScale).px,
                    pos = at((-50).percent)
                )
            }
        }
    }

    private fun ElementScope<*>.renderTiles(config: MapConfig, room: OdonRoom) {
        val components = room.roomTiles
        if (components.isEmpty()) return

        val (scale, radius, _, _, _, _, _, autoClear) = config

        val c = {
            val base = room.data.colour
            if (autoClear && Dungeon.currentRoom == room) base.mix(InteractiveMap.roomInCol.withAlpha(255), InteractiveMap.roomInCol.alpha).rgb else base.rgb
        }

        val outlineCol = Colour.Animated(
            from = Colour.TRANSPARENT,
            to = Colour.WHITE.withAlpha(180)
        )

        val byPos = components.associateBy { it.placement }
        fun has(p: Vec2i, dx: Int, dz: Int) = byPos.containsKey(p.add(dx, dz))

        val lCorners = components.filter {
            val p = it.placement
            val (n, s, w, e) = listOf(has(p, 0, -20), has(p, 0, 20), has(p, -20, 0), has(p, 20, 0))
            val long = (n && s) || (w && e)
            val twoByTwo =
                (n && e && has(p, 20, -20)) ||
                        (n && w && has(p, -20, -20)) ||
                        (s && e && has(p, 20, 20)) ||
                        (s && w && has(p, -20, 20))

            // l corner: 2 connections, not a line, not a part of 2x2
            listOf(n, s, w, e).count { c -> c } == 2 && !long && !twoByTwo
        }.map { it.placement }.toSet()


        components.forEach { comp ->

            val col = Colour.Animated(
                from = colour { c() },
                to = colour { c().multiply(1.15f) }
            )

            val p = comp.placement
            val isL = p in lCorners

            val (n, s, w, e) = listOf(has(p, 0, -20), has(p, 0, 20), has(p, -20, 0), has(p, 20, 0))

            // all blocks start at 16x 16
            // if L corner don't touch
            // if next to L corner 4
            // if 2x2 or long 2
            fun gap(exists: Boolean, dx: Int, dz: Int) = when {
                !exists || isL -> 0f
                p.add(dx, dz) in lCorners -> 4f
                else -> 2f
            }

            val ng = gap(n, 0, -20)
            val sg = gap(s, 0, 20)
            val wg = gap(w, -20, 0)
            val eg = gap(e, 20, 0)

            val width = (16f + wg + eg) * scale
            val height = (16f + ng + sg) * scale

            block(
                constrain(((p.x - wg) * scale).px, ((p.z - ng) * scale).px, width.px, height.px),
                colour = col,
                radius = radius(
                    tl = if (n || w) 0 else radius,
                    bl = if (s || w) 0 else radius,
                    br = if (s || e) 0 else radius,
                    tr = if (n || e) 0 else radius
                )
            ) {
                if (autoClear) {

                    outline(outlineCol, 2.px)

                    // this shit a bit overlaps needed lines
                    // but looks good enough so I'm not fixing it.

                    val mx = wg * scale + 0.5f
                    val my = ng * scale + 0.5f
                    val mw = width - eg * scale - 0.5f
                    val mh = height - sg * scale - 0.5f

                    fun mask(vert: Boolean, start: Float, end: Float, pos: Float) {
                        val constraints =
                            if (vert)
                                constrain(pos.px, start.px, 4.px, (end - start).px)
                            else
                                constrain(start.px, pos.px, (end - start).px, 4.px)
                        block(constraints, colour = col)
                    }

                    if (n) mask(false,
                        start = if (has(p, -20, -20)) -1f else mx,
                        end   = if (has(p, 20, -20)) width + 1f else mw,
                        pos   = -2f
                    )

                    if (s) mask(false,
                        start = if (has(p, -20, 20)) -1f else mx,
                        end   = if (has(p, 20, 20)) width + 1f else mw,
                        pos   = height - 2f
                    )

                    if (w) mask(true,
                        start = if (has(p, -20, -20)) -1f else my,
                        end   = if (has(p, -20, 20)) height + 1f else mh,
                        pos   = -2f
                    )

                    if (e) mask(true,
                        start = if (has(p, 20, -20)) -1f else my,
                        end   = if (has(p, 20, 20)) height + 1f else mh,
                        pos   = width - 2f
                    )

                    onMouseEnterExit {
                        col.animate(0.15.seconds, style = Animation.Style.EaseOutQuint)
                        outlineCol.animate(0.15.seconds, style = Animation.Style.EaseOutQuint)
                    }
                    onClick(nonSpecific = true) { (button) ->
                        InteractiveMap.getRoomPath(room, comp, button)
                        true
                    }
                }
            }
        }
    }

    private fun ElementScope<*>.renderName(config: MapConfig, room: OdonRoom) {
        if (room.data.type in listOf(RoomType.ENTRANCE, RoomType.FAIRY, RoomType.BLOOD)) return
        val (scale, _, font, fontScale, shadow) = config

        val lines = room.name.split(" ")
        val textSize = 18f * fontScale

        lines.forEachIndexed { i, s ->
            val tw = if (font.name == "Minecraft") s.width(textSize / mc.font.lineHeight) else NVGRenderer.textWidth(s, textSize, font)
            val x = (room.textPlacement.x * scale) + 8f * scale - (tw / 2f)
            val y = (room.textPlacement.z * scale) + 8f * scale - (lines.size * textSize / 2f) + (i * textSize)

            text(
                string = s,
                size = textSize.px,
                colour = colour { room.textColour.rgb },
                font = font,
                pos = at(x = x.px, y = y.px)
            ).shadow = shadow
        }
    }

    fun onChunkLoad() {
        if (mapSize.x == 0 && mapSize.z == 0) {
            mapSize = when (floor?.floorNumber) {
                0 -> Vec2i(4, 4)
                1 -> Vec2i(4, 5)
                2, 3 -> Vec2i(5, 5)
                else -> Vec2i(6, 6)
            }
            refresh()
        }
    }

    fun update(packet: ClientboundMapItemDataPacket) {
        if (packet.mapId.id and 1000 != 0) return
        val colours = mc.level?.getMapData(packet.mapId)?.colors ?: return

        if (startCoords == null) {
            val (greenStart, greenLength) = findGreenRoom(colours)
            if (greenLength != 16 && greenLength != 18) return

            roomSize = greenLength

            val (start, center, size) = when (floor?.floorNumber) {
                0 -> Triple(Vec2i(22, 22), Vec2i(-137, -137), Vec2i(4, 4))
                1 -> Triple(Vec2i(22, 11), Vec2i(-137, -121), Vec2i(4, 5))
                2, 3 -> Triple(Vec2i(11, 11), Vec2i(-121, -121), Vec2i(5, 5))
                else -> {
                    val s = Vec2i((greenStart and 127) % (greenLength + 4), (greenStart shr 7) % (greenLength + 4))
                    val extraX = if (s.x == 5) 1 else 0
                    val extraZ = if (s.z == 5) 1 else 0

                    Triple(
                        s,
                        Vec2i(-121 + (extraX * 16), -121 + (extraZ * 16)),
                        Vec2i(5 + extraX, 5 + extraZ)
                    )
                }
            }

            startCoords = start
            mapCentre = center
            mapSize = size
        }

        packet.decorations.getOrNull()?.let { decorations ->
            val playerIterator = Dungeon.dungeonTeammates.iterator()

            decorations.forEach { decoration ->
                if (decoration.type.value() == MapDecorationTypes.FRAME.value()) return@forEach

                val player = playerIterator.asSequence().firstOrNull { !it.isDead } ?: return@forEach

                player.mapPos = Vec2i(decoration.x.toInt(), decoration.y.toInt())
                player.yaw = decoration.rot() * 360 / 16f
            }
        }

        val rs = roomSize ?: return
        val halfRoom = rs / 2
        val halfTile = halfRoom + 2
        val centreX = startCoords!!.x + halfRoom
        val centreZ = startCoords!!.z + halfRoom


        for (x in 0..10) {
            for (z in 0..10) {
                val tile = ScanUtils.grid[z * 11 + x] ?: continue

                val mapX = centreX + x * halfTile
                val mapY = centreZ + z * halfTile

                if (mapX in 0..127 && mapY in 0..127) {
                    val col = colours[mapY * 128 + mapX].toInt() and 0xFF

                    when (tile) {
                        is OdonRoom -> {
                            val stateCol = if (tile.roomTiles.size > 1) {
                                val topLeft = tile.roomTiles.minBy { it.x * 1000 + it.z }

                                val gx = (topLeft.x + 185) / 16
                                val gz = (topLeft.z + 185) / 16

                                val tx = centreX + gx * halfTile
                                val ty = centreZ + gz * halfTile

                                if (tx in 0..127 && ty in 0..127) colours[ty * 128 + tx].toInt() and 0xFF else col
                            } else col

                            tile.updateState(stateCol)
                        }
                        is OdonDoor -> tile.updateState(col)
                    }
                }
            }
        }
    }

    fun reset() {
        startCoords = null
        roomSize = null
        mapCentre = Vec2i(0, 0)
        refreshShit = 0
        mapSize = Vec2i(0, 0)
    }

    private fun findGreenRoom(mapData: ByteArray): Pair<Int, Int> {
        var start = -1
        var length = 0
        for (i in mapData.indices) {
            if (mapData[i].toInt() == 30) {
                if (length++ == 0) start = i
            } else {
                if (length >= 16) return start to length
                length = 0
            }
        }
        return start to length
    }

    data class MapConfig(
        val scale: Float = 3f,
        val radius: Float = DungeonMap.roomRadius,
        val font: Font = defaultFont,
        val fontScale: Float = DungeonMap.fontScale,
        val shadow: Boolean = DungeonMap.shadow,
        val icons: Boolean = true,
        val icon: IconConfig = IconConfig(),
        val autoClear: Boolean = false
    )

    data class IconConfig(
        val scale: Float = DungeonMap.iconScale,
        val heads: Boolean = DungeonMap.showHeads,
        val ownHead: Boolean = DungeonMap.showOwnHead,
        val border: Boolean = DungeonMap.iconBorder,
        val borderColour: Colour = DungeonMap.iconBorderColour,
        val classColour: Boolean = DungeonMap.classColour,
        val thickness: Int = DungeonMap.iconBorderThickness,
        val name: Boolean = DungeonMap.showNames,
        val whenLeap: Boolean = DungeonMap.whenLeap,
        val nameScale: Float = DungeonMap.nameScale,
    )
}