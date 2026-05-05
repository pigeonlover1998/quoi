package quoi.module.impl.dungeon.autop3

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.config.typeName
import quoi.module.impl.dungeon.autop3.rings.P3Action
import quoi.utils.render.*
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class P3Ring(
    val x: Double,
    val y: Double,
    val z: Double,
    val action: P3Action,
    val subActions: List<P3Action> = emptyList(),
    val radius: Double = 0.5,
    val height: Double? = null,
    val delay: Int? = null,
    val trigger: Boolean = false,
    val ground: Boolean = false,
    val term: Boolean = false,
    val termclose: Boolean = false
) {
    
    @Transient
    var triggered = false
    
    @Transient
    var active = false
    
    @Transient
    var triggerConsumed = false
    
    @Transient
    var termConsumed = false
    
    @Transient
    var termcloseConsumed = false

    fun boundingBox(): AABB {
        val h = height ?: (radius * 2)
        return AABB(
            x - radius, y, z - radius,
            x + radius, y + h, z + radius
        )
    }

    fun isInNode(curr: Vec3, prev: Vec3): Boolean {
        val box = boundingBox()
        val feet = AABB(curr.x - 0.2, curr.y, curr.z - 0.2, curr.x + 0.3, curr.y + 0.5, curr.z)
        val intercept = box.clip(curr, prev).isPresent
        val intersects = box.intersects(feet)
        return intercept || intersects
    }
    
    fun updateState(playerPos: Vec3, oldPos: Vec3): Boolean {
        val inNode = isInNode(playerPos, oldPos)
        
        if (inNode && !triggered) {
            return true
        }
        
        if (!inNode && triggered) {
            reset()
        }
        return false
    }
    
    fun setTriggered() {
        triggered = true
    }
    
    fun setActive() {
        active = true
    }
    
    fun setInactive() {
        active = false
    }
    
    fun reset() {
        triggered = false
        triggerConsumed = false
        termConsumed = false
        termcloseConsumed = false
    }
    
    fun checkTriggerArg(): Boolean {
        if (trigger && !triggerConsumed) return true
        if (ground && mc.player?.onGround() != true) return true
        if (term && !termConsumed) return true
        if (termclose && !termcloseConsumed) return true
        return false
    }
    
    fun consumeTrigger() {
        triggerConsumed = true
    }
    
    fun consumeTerm() {
        termConsumed = true
    }
    
    fun consumeTermClose() {
        termcloseConsumed = true
    }
    
    fun render(ctx: WorldRenderContext, colour: Colour, fillColour: Colour, mode: String, thickness: Float, height: Float, depth: Boolean) {
        val box = boundingBox()
        val center = Vec3(x, y, z)
        
        when (mode) {
            "Silent" -> return
            
            "Circle" -> {
                val segments = 64
                val points = mutableListOf<Vec3>()
                for (i in 0..segments) {
                    val angle = (i.toDouble() / segments) * Math.PI * 2
                    val px = x + radius * cos(angle)
                    val pz = z + radius * sin(angle)
                    points.add(Vec3(px, y + 0.01, pz))
                }
                ctx.drawLine(points, colour, depth, thickness)
            }
            
            "Wireframe" -> {
                ctx.drawWireFrameBox(box, colour, thickness, depth)
            }
            
            "Filled" -> { 
                ctx.drawFilledBox(box, colour.withAlpha(60f / 255f), depth)
                ctx.drawWireFrameBox(box, colour, thickness, depth)
            }
            
            "Flat" -> {
                val flatBox = AABB(
                    x - radius, y, z - radius,
                    x + radius, y + 0.05, z + radius
                )
                ctx.drawFilledBox(flatBox, colour.withAlpha(80f / 255f), depth)
            }
            
            "Cylinder" -> {
                ctx.drawCylinder(center, radius.toFloat(), height, colour, thickness = thickness, depth = depth)
            }
            
            "Corners" -> {
                val len = min(min(box.maxX - box.minX, box.maxZ - box.minZ), box.maxY - box.minY) * 0.25
                val x0 = box.minX; val x1 = box.maxX
                val y0 = box.minY; val y1 = box.maxY
                val z0 = box.minZ; val z1 = box.maxZ
                
                listOf(
                    Vec3(x0, y0, z0) to Vec3(x0 + len, y0, z0),
                    Vec3(x0, y0, z0) to Vec3(x0, y0 + len, z0),
                    Vec3(x0, y0, z0) to Vec3(x0, y0, z0 + len),
                    Vec3(x1, y0, z0) to Vec3(x1 - len, y0, z0),
                    Vec3(x1, y0, z0) to Vec3(x1, y0 + len, z0),
                    Vec3(x1, y0, z0) to Vec3(x1, y0, z0 + len),
                    Vec3(x0, y1, z0) to Vec3(x0 + len, y1, z0),
                    Vec3(x0, y1, z0) to Vec3(x0, y1 - len, z0),
                    Vec3(x0, y1, z0) to Vec3(x0, y1, z0 + len),
                    Vec3(x1, y1, z0) to Vec3(x1 - len, y1, z0),
                    Vec3(x1, y1, z0) to Vec3(x1, y1 - len, z0),
                    Vec3(x1, y1, z0) to Vec3(x1, y1, z0 + len),
                    Vec3(x0, y0, z1) to Vec3(x0 + len, y0, z1),
                    Vec3(x0, y0, z1) to Vec3(x0, y0 + len, z1),
                    Vec3(x0, y0, z1) to Vec3(x0, y0, z1 - len),
                    Vec3(x1, y0, z1) to Vec3(x1 - len, y0, z1),
                    Vec3(x1, y0, z1) to Vec3(x1, y0 + len, z1),
                    Vec3(x1, y0, z1) to Vec3(x1, y0, z1 - len),
                    Vec3(x0, y1, z1) to Vec3(x0 + len, y1, z1),
                    Vec3(x0, y1, z1) to Vec3(x0, y1 - len, z1),
                    Vec3(x0, y1, z1) to Vec3(x0, y1, z1 - len),
                    Vec3(x1, y1, z1) to Vec3(x1 - len, y1, z1),
                    Vec3(x1, y1, z1) to Vec3(x1, y1 - len, z1),
                    Vec3(x1, y1, z1) to Vec3(x1, y1, z1 - len),
                ).forEach { (a, b) -> ctx.drawLine(listOf(a, b), colour, depth, thickness) }
            }
            
            "Label" -> {
                val flatBox = AABB(
                    x - radius, y, z - radius,
                    x + radius, y + 0.05, z + radius
                )
                ctx.drawFilledBox(flatBox, colour.withAlpha(50f / 255f), depth)
                ctx.drawText(
                    Component.literal(action.typeName),
                    center.add(0.0, (box.maxY - box.minY) * 0.5 + 0.3, 0.0),
                    colour,
                    depth = depth
                )
            }
            
            else -> { // "Nodes"
                val flatBox = AABB(
                    x - radius, y, z - radius,
                    x + radius, y + 0.05, z + radius
                )
                ctx.drawFilledBox(flatBox, fillColour.withAlpha(50f / 255f), depth)
                
                val dx = (box.maxX - box.minX) * 0.15
                val dz = (box.maxZ - box.minZ) * 0.15
                val inlineBox = AABB(
                    box.minX + dx, box.minY, box.minZ + dz,
                    box.maxX - dx, box.minY + 0.05, box.maxZ - dz
                )
                ctx.drawWireFrameBox(inlineBox, colour, thickness, depth)
            }
        }
    }
}
