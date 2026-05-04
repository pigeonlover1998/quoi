package quoi.api.autoroutes2.converters

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import quoi.api.autoroutes2.RouteNode
import quoi.api.autoroutes2.awaits.SecretAwait
import quoi.api.autoroutes2.nodes.BoomNode
import quoi.api.autoroutes2.nodes.BreakerNode
import quoi.api.autoroutes2.nodes.EtherwarpNode
import quoi.api.autoroutes2.nodes.UseItemNode
import quoi.config.ConfigSystem.gson
import quoi.module.impl.dungeon.autoclear.impl.AutoRoutes
import quoi.utils.addVec
import quoi.utils.toDirection
import java.io.File

object RsaConverter {
    fun convert(file: File) {
        val type = object : TypeToken<Map<String, List<JsonObject>>>() {}.type
        val data: Map<String, List<JsonObject>> = gson.fromJson(file.readText(), type)

        data.forEach { (room, rings) ->
            val new = AutoRoutes.routeNodes.getOrPut(room) { mutableListOf() }

            rings.forEach { ring ->
                val type = ring.get("type").asString

                val node: RouteNode = when (type) {
                    "etherwarp" -> EtherwarpNode().apply {
                        target = vec(ring.get("localTarget"))
                    }
                    "use" -> UseItemNode().apply {
                        val rotation = gson.fromJson(ring.get("rotationVec"), Vec3::class.java)
                        val dir = rotation.toDirection()
                        yaw = dir.yaw
                        pitch = dir.pitch
                        item = ring.get("itemID").asString
                    }
                    "boom" -> BoomNode().apply {
                        target = vec(ring.get("target"))
                    }
                    "break" -> BreakerNode().apply {
                        blocks = ring.getAsJsonArray("blocks").map {
                            bp(it)
                        }
                    }
                    "bat" -> UseItemNode().apply {
                        yaw = ring.get("yaw").asFloat
                        pitch = ring.get("pitch").asFloat
                        item = "HYPERION"
                    }
                    else -> return@forEach
                }

                node.relative = vec(ring.get("localPos"))
                node.radius = ring.get("radius").asFloat.takeIf { it != 0.5f }
                node.start = ring.get("start").asBoolean.takeIf { it }
                node.unsneak = ring.get("sneak")?.asBoolean?.takeIf { it }

                node.awaits = ring.getAsJsonObject("awaits")?.let {
                    listOfNotNull(
                        it.get("awaitSecrets")?.asInt?.let { amount -> SecretAwait(amount) }
                        //
                    ).toMutableList()
                }

                if (type == "bat") {
                    if (node.awaits == null) node.awaits = mutableListOf()
                    node.awaits?.add(SecretAwait(1))
                }

                new.add(node)
            }
        }
    }

    private fun vec(a: JsonElement): Vec3 {
        return gson.fromJson(a, Vec3::class.java).addVec(x = 15, z = 15)
    }

    private fun bp(a: JsonElement): BlockPos {
        return gson.fromJson(a, BlockPos::class.java).mutable().move(15, 0, 15).immutable()
    }
}