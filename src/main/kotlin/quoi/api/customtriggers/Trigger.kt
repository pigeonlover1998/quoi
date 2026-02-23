package quoi.api.customtriggers

import quoi.api.customtriggers.actions.TriggerAction
import quoi.api.customtriggers.conditions.TriggerCondition
import java.util.UUID

data class Trigger(
    val id: String,
    var name: String,
//    val group: String? = null,
    var enabled: Boolean = true,
    val conditions: MutableList<TriggerCondition> = mutableListOf(),
    val actions: MutableList<DelayedAction> = mutableListOf()
) {
    constructor(name: String) : this(
        id = UUID.randomUUID().toString(),
        name = name
    )
    @Transient var state = false
}

data class DelayedAction(
    val action: TriggerAction,
    var delay: Int = 0,
    var server: Boolean = false
)