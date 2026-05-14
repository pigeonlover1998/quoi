package quoi.api.autoroutes

import quoi.api.autoroutes.awaits.RaycastAwait
import quoi.api.autoroutes.awaits.SecretAwait
import quoi.api.autoroutes.nodes.BoomNode
import quoi.api.autoroutes.nodes.BreakerNode
import quoi.api.autoroutes.nodes.EtherwarpNode
import quoi.api.autoroutes.nodes.RotateNode
import quoi.api.autoroutes.nodes.UseItemNode
import quoi.config.TypeName
import kotlin.error
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation

object RouteRegistry {
    val _nodeTypes = listOf(
        RotateNode::class,
        BoomNode::class,
        BreakerNode::class,
        UseItemNode::class,
        EtherwarpNode::class
    )

    val awaitTypes = listOf(
        SecretAwait::class,
        RaycastAwait::class
    )
    
    val nodeEntries by lazy { getNodeEntries() }
    val nodeTypes by lazy { nodeEntries.map { it.first } }

    @JvmName("getNodeEntries_")
    private fun getNodeEntries(): List<Pair<String, () -> RouteNode>> =
        _nodeTypes.map { kClass ->
            val name = kClass.findAnnotation<TypeName>()?.value ?: error("this should never happen")
            val instance = {
                runCatching {
                    kClass.createInstance()
                }.getOrNull() ?: error("no good. ensure all constructors have default params (x: Int = 0, etc)")
            }
            name to instance
        }
}