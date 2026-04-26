package quoi.api.autoroutes2

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.vec.MutableVec3
import quoi.config.TypeNamed
import quoi.utils.ChatUtils.literal
import quoi.utils.component1
import quoi.utils.component2
import quoi.utils.component3
import quoi.utils.render.drawCylinder
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawText
import quoi.utils.render.drawWireFrameBox

abstract class RouteNode(
    var relative: Vec3 = Vec3.ZERO,
    var radius: Float? = null,
    var height: Float? = null,
    var start: Boolean? = null,
    var unsneak: Boolean? = null,
    var awaits: MutableList<RouteAwait>? = null,
    var chain: RouteChain? = null
) : TypeNamed {

    @Transient
    lateinit var pos: Vec3
    @Transient
    lateinit var aabb: AABB
    @Transient
    var triggered = false

    protected fun cancel() {
        triggered = false
    }

    fun inside(pos: MutableVec3): Boolean =
        pos.inside(aabb)

    fun inside(player: LocalPlayer): Boolean =
        player.boundingBox.intersects(aabb)

    abstract val colour: Colour
    abstract fun execute(player: LocalPlayer, pos: MutableVec3): Boolean

    open val priority: Int
        get() = 0

    open fun create(player: LocalPlayer, room: OdonRoom): RouteNode? {
        return this
    }

    open fun update(room: OdonRoom) {
        pos = room.getRealCoords(relative)
        val (x, y, z) = pos
        val r = radius ?: 0.5f
        aabb = AABB(
            x - r, y, z - r,
            x + r, y + (height ?: 0.1f), z + r
        )
        awaits?.forEach { it.reset() }
    }

    open fun render(ctx: WorldRenderContext, style: String, colour: Colour, fillColour: Colour, activeColour: Colour, thickness: Float) {
        val start = start ?: false
        val depth = !start
        when (style) {
            "Box" -> {
                if (start) ctx.drawFilledBox(aabb, fillColour, false)
                ctx.drawWireFrameBox(aabb, colour, thickness, depth)
            }
            "Filled box" -> {
                ctx.drawFilledBox(aabb, fillColour, depth)
                ctx.drawWireFrameBox(aabb, colour, thickness, depth)
            }
            "Cylinder" -> ctx.drawCylinder(pos, radius ?: 0.5f, height ?: 0.1f, colour, thickness = thickness, depth = depth)
        }

        if (/*AutoRoutes2.editMode && */chain != null) {
            ctx.drawText(
                text = literal("${chain!!.index}"),
                pos = pos.add(0.0, 0.3, 0.0),
                scale = 1.2f,
                depth = true
            )
        }
    }

    fun onSecret() {
        awaits?.forEach { it.onSecret() }
    }

    fun checkAwaits(player: LocalPlayer): Boolean {
        return awaits?.all { it.check(player, this) } ?: true
    }
}