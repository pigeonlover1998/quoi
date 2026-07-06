package quoi.module.impl.render

import quoi.api.events.EntityEvent
import quoi.api.events.RenderEvent
import quoi.api.events.core.on
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.EntityUtils.colourFromDistance
import quoi.utils.EntityUtils.interpolatedBox
import quoi.utils.EntityUtils.playerEntitiesNoSelf
import quoi.utils.WorldUtils

@Suppress("UNNECESSARY_SAFE_CALL")
object PlayerESP : Module(
    "Player ESP",
    desc = "Highlights players through walls."
) {
    private val ironmenOnly by switch("Ir*nmen only")

    private var specific by switch("Specific player")
    private var specificName by textInput("Name", length = 16, placeholder = "Player name").childOf(::specific)
        .suggests { WorldUtils.players.map { it.profile.name } }

    private val tracer = tracer(customColour = true)
    private val highlight = highlight(customColour = true, customFillColour = true, aabbOffset = true)

    init {
        on<RenderEvent.World> {
            playerEntitiesNoSelf.forEach { entity ->
                if (ironmenOnly && entity.displayName?.string?.contains("♲") == false) return@forEach
                if (specific && entity.displayName?.string?.contains(specificName) == false) return@forEach
                highlight.draw(ctx, entity.interpolatedBox, entity.colourFromDistance, entity.colourFromDistance)
                tracer.draw(ctx, entity, entity.colourFromDistance)
            }
        }

        on<EntityEvent.ForceGlow> {
            if (highlight.style != "Glow") return@on
            if (ironmenOnly && entity.displayName?.string?.contains("♲") == false) return@on
            if (entity !in playerEntitiesNoSelf) return@on
            highlight.draw(this, entity.colourFromDistance)
        }
    }
}