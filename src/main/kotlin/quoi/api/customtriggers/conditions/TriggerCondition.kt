package quoi.api.customtriggers.conditions

import quoi.api.customtriggers.Abobable
import quoi.api.customtriggers.TriggerContext
import quoi.config.TypeNamed

sealed interface TriggerCondition : TypeNamed, Abobable {
    fun matches(ctx: TriggerContext): Boolean
}