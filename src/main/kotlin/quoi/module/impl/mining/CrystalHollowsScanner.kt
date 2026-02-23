package quoi.module.impl.mining

import quoi.QuoiMod.scope
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.RenderEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.Island
import quoi.module.Module
import quoi.module.impl.mining.CrystalHollowsMap.X_MAX
import quoi.module.impl.mining.CrystalHollowsMap.X_MIN
import quoi.module.impl.mining.CrystalHollowsMap.Z_MAX
import quoi.module.impl.mining.CrystalHollowsMap.Z_MIN
import quoi.module.impl.mining.CrystalHollowsMap.isDirty
import quoi.module.impl.mining.enums.Structure
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.ColourSetting
import quoi.module.settings.impl.NumberSetting
import quoi.module.settings.impl.SelectorSetting
import quoi.utils.ChatUtils
import quoi.utils.ChatUtils.literal
import quoi.utils.EntityUtils.renderX
import quoi.utils.EntityUtils.renderY
import quoi.utils.EntityUtils.renderZ
import quoi.utils.WorldUtils.registryName
import quoi.utils.aabb
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawStyledBox
import quoi.utils.render.drawText
import quoi.utils.vec3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.LevelChunk
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sqrt

// https://github.com/RoseGoldIsntGay/GumTuneClient/blob/main/src/main/java/rosegold/gumtuneclient/modules/world/WorldScanner.java
object CrystalHollowsScanner : Module(
    "Crystal Hollows Scanner",
    area = Island.CrystalHollows
) {
    private val structureScanner by BooleanSetting("Structure scanner")
    val routeScanner by BooleanSetting("Route scanner")
    private val style by SelectorSetting("Style", "Box", arrayListOf("Box", "Filled box"), desc = "Esp render style to be used.").withDependency { routeScanner }
    private val distCols by BooleanSetting("Distance colours").withDependency { routeScanner }
    val colour by ColourSetting("Colour", Colour.WHITE, allowAlpha = true).withDependency { routeScanner && !distCols }
    private val fillDistCols by BooleanSetting("Fill distance colours").withDependency { style.selected == "Filled box" && routeScanner }
    private val fillColour by ColourSetting("Fill colour", Colour.WHITE.withAlpha(0.33f), allowAlpha = true).withDependency { style.selected == "Filled box" && routeScanner && !fillDistCols }
    private val thickness by NumberSetting("Thickness", 4f, 1f, 8f, 1f).withDependency { routeScanner }

    val scannedChunks = HashSet<Long>()
    private val foundStructures = ConcurrentHashMap<Structure, MutableList<BlockPos>>()
    val foundRouteBlocks = mutableListOf<BlockPos>()

    init {
        on<WorldEvent.Chunk.Load> {
            if (!structureScanner && !routeScanner) return@on
            scope.launch(Dispatchers.IO) {
                try {
                    val chunkX = chunk.pos.x shl 4
                    val chunkZ = chunk.pos.z shl 4

                    if (chunkX !in X_MIN..X_MAX || chunkZ !in Z_MIN..Z_MAX) return@launch

                    val chunkKey = chunk.pos.toLong()

                    if (!scannedChunks.contains(chunkKey)) {
                        scannedChunks.add(chunkKey)
                        handleChunk(chunk)
                        if (CrystalHollowsMap.enabled) isDirty = true
                    }
                } catch (_: Exception) { }
            }
        }

        on<RenderEvent.World> {
            if (structureScanner) foundStructures.forEach { (structure, positions) ->
                positions.forEach { blockPos ->
                    val pos = blockPos.vec3
                    val dist = pos.distanceToSqr(player.renderX, player.renderY, player.renderZ)
                    val scale = (0.5 + dist.pow(0.5) / 10.0).toFloat()
                    ctx.drawFilledBox(pos.aabb, Colour.WHITE)
                    ctx.drawText(literal(structure.displayName).withColor(structure.colour.rgb), pos, scale = scale)
                }
            }

            if (routeScanner) foundRouteBlocks.forEach { blockPos ->
                var currentDistCol: Colour? = null
                if (distCols || fillDistCols) {
                    val dist = sqrt(player.distanceToSqr(blockPos.x + 0.5, blockPos.y + 0.5, blockPos.z + 0.5))
                    val hue = (1f - (dist / 64f).coerceIn(0.0, 1.0)).toFloat() * 0.33f
                    currentDistCol = Colour.HSB(hue, 1f, 1f)
                }

                val c = if (distCols && currentDistCol != null) currentDistCol else colour
                val fc = if (fillDistCols && currentDistCol != null) currentDistCol.withAlpha(fillColour.alpha) else fillColour

                ctx.drawStyledBox(style.selected, blockPos.aabb, c, fc, thickness, false)
            }
        }

        on<WorldEvent.Change> {
            scannedChunks.clear()
            foundStructures.clear()
            foundRouteBlocks.clear()
        }
    }

    private fun handleChunk(chunk: LevelChunk) {
        val fromY = if (structureScanner && !routeScanner) 30 else 0
        val toY = if (routeScanner && !structureScanner) 70 else 180
        for (x in 0..15) {
            for (z in 0..15) {
                for (y in fromY..toY) {
                    val pos = BlockPos(chunk.pos.minBlockX + x, y, chunk.pos.minBlockZ + z)

                    if (structureScanner) Structure.entries.forEach { structure ->
                        if (!structure.quarter.test(pos)) return@forEach

                        if (structure.canBeMultiple) {
                            val existing = foundStructures[structure] ?: mutableListOf()
                            if (existing.any { it.isWithinChunks(pos, 4) }) return@forEach
                        } else {
                            if (foundStructures.containsKey(structure)) return@forEach
                        }

                        if (scanStructure(chunk, structure, x, y, z)) {
                            val realPos = pos.offset(structure.xOffset, structure.yOffset, structure.zOffset)
                            foundStructures.computeIfAbsent(structure) { mutableListOf() }.add(realPos)
                            ChatUtils.modMessage("Found ${structure.displayName} at ${pos.x}, ${pos.y}, ${pos.z}")
                        }
                    }

                    if (routeScanner) {
                        if (chunk.getBlockState(pos).block != Blocks.COBBLESTONE || y > 70) continue
                        val valid = Direction.entries.filter { it != Direction.DOWN }.all {
                            val state = chunk.level.getBlockState(pos.relative(it))
                            state.isAir || state.block.registryName.contains("glass")
                        }

                        if (valid && pos !in foundRouteBlocks) {
                            foundRouteBlocks.add(pos)
                        }
                    }
                }
            }
        }
    }

    private fun scanStructure(chunk: LevelChunk, structure: Structure, x: Int, startY: Int, z: Int): Boolean {
        if (startY + structure.blocks.size >= 180) return false

        structure.blocks.forEachIndexed { i, block ->
            if (block == null) return@forEachIndexed

            val checkY = startY + i

            val pos = BlockPos(chunk.pos.minBlockX + x, checkY, chunk.pos.minBlockZ + z)

            val state = chunk.getBlockState(pos)

            if (state.block != block) {
                return false
            }
        }
        return true
    }

    private fun BlockPos.isWithinChunks(other: BlockPos, chunks: Int): Boolean {
        val dx = (this.x shr 4) - (other.x shr 4)
        val dz = (this.z shr 4) - (other.z shr 4)
        return dx.absoluteValue <= chunks && dz.absoluteValue <= chunks
    }
}