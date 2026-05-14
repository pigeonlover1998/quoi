package quoi.api.autoroutes.converters

import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import quoi.api.autoroutes.RouteChain
import quoi.api.autoroutes.RouteNode
import quoi.api.autoroutes.awaits.SecretAwait
import quoi.api.autoroutes.nodes.BoomNode
import quoi.api.autoroutes.nodes.BreakerNode
import quoi.api.autoroutes.nodes.EtherwarpNode
import quoi.api.autoroutes.nodes.RotateNode
import quoi.api.autoroutes.nodes.UseItemNode
import quoi.config.ConfigSystem.gson
import quoi.module.impl.dungeon.autoclear.impl.AutoRoutes
import java.io.File

object LegacyConverter {
    fun convert(file: File) {
        val type = object : TypeToken<Map<String, List<JsonObject>>>() {}.type
        val data: Map<String, List<JsonObject>> = gson.fromJson(file.readText(), type)

        data.forEach { (room, rings) ->
            val new = AutoRoutes.routeNodes.getOrPut(room) { mutableListOf() }

            val chains = mutableMapOf<String, Int>()
            val starts = rings.filter { it.getAsJsonObject("action").get("type").asString == "start" }.map {
                Vec3(it.get("x").asDouble, it.get("y").asDouble, it.get("z").asDouble)
            }
            val unsneaks = rings.filter { it.getAsJsonObject("action").get("type").asString == "unsneak" }.map {
                Vec3(it.get("x").asDouble, it.get("y").asDouble, it.get("z").asDouble)
            }

            rings.forEach { ring ->
                val action = ring.getAsJsonObject("action")
                val type = action.get("type").asString

                val node: RouteNode = when (type) {
                    "etherwarp" -> EtherwarpNode().apply {
                        yaw = action.get("yaw")?.asFloat ?: 0f
                        pitch = action.get("pitch")?.asFloat ?: 0f
                    }
                    "rotate" -> RotateNode().apply {
                        yaw = action.get("yaw").asFloat
                        pitch = action.get("pitch").asFloat
                    }
                    "boom" -> BoomNode().apply {
                        yaw = action.get("yaw").asFloat
                        pitch = action.get("pitch").asFloat
                    }
                    "use_item" -> UseItemNode().apply {
                        val old = action.get("itemName").asString
                        item = when(old) {
                            "aspectofthevoid" -> "ASPECT_OF_THE_VOID"
                            "hyperion" -> "HYPERION"
                            "spiritsceptre" -> "BAT_WAND"
                            else -> old
                        }
                        yaw = action.get("yaw").asFloat
                        pitch = action.get("pitch").asFloat
                    }
                    "dungeon_breaker" -> BreakerNode().apply {
                        blocks = action.getAsJsonArray("blocks").map {
                            gson.fromJson(it, BlockPos::class.java)
                        }
                    }
                    else -> return@forEach
                }

                val pos = Vec3(
                    ring.get("x").asDouble,
                    ring.get("y").asDouble,
                    ring.get("z").asDouble
                )

                node.relative = pos
                node.radius = ring.get("radius")?.asFloat?.div(2f)
                node.height = ring.get("height")?.asFloat

                if (starts.any { it.distanceToSqr(pos) < 0.001 }) {
                    node.start = true
                }

                if (unsneaks.any { it.distanceToSqr(pos) < 0.001 }) {
                    node.unsneak = true
                }

                node.awaits = ring.getAsJsonArray("arguments")
                    ?.map { it.asJsonObject }
                    ?.filter { it.get("type").asString == "await_secret" }
                    ?.map { SecretAwait(it.get("amount")?.asInt ?: 1) }
                    ?.toMutableList()

                val chain = ring.get("chain")?.asString
                if (!chain.isNullOrEmpty()) {
                    val i = chains.getOrDefault(chain, 0)
                    node.chain = RouteChain(chain, i)
                    chains[chain] = i + 1
                }

                new.add(node)
            }
        }
    }
}