package quoi.api.customtriggers.actions

import quoi.api.customtriggers.Abobable
import quoi.api.customtriggers.TriggerContext
import quoi.config.TypeNamed

sealed interface TriggerAction : TypeNamed, Abobable {
    fun execute(ctx: TriggerContext)
}