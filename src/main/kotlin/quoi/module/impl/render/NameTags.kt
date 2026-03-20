package quoi.module.impl.render

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import quoi.api.colour.Colour
import quoi.api.events.RenderEvent
import quoi.api.skyblock.dungeon.Dungeon.dungeonTeammatesNoSelf
import quoi.api.skyblock.dungeon.Dungeon.inDungeons
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.ChatUtils.literal
import quoi.utils.EntityUtils.colourFromDistance
import quoi.utils.EntityUtils.distanceToCamera
import quoi.utils.EntityUtils.playerEntitiesNoSelf
import quoi.utils.EntityUtils.renderPos
import quoi.utils.EntityUtils.renderX
import quoi.utils.EntityUtils.renderY
import quoi.utils.EntityUtils.renderZ
import quoi.utils.StringUtils.toFixed
import quoi.utils.render.drawText
import kotlin.math.pow

object NameTags : Module(
    "Name Tags",
    desc = "Customisable nametags for entities."
) {
    private val customTag by switch("Custom nametag").json("Custom nametags toggle")
    private val dungeonsOnly by switch("Dungeons only").childOf(::customTag)
    private val simpleTag by switch("Simple tag").childOf(::customTag)
    private val customTagBgColour by colourPicker("Background colour", Colour.RGB(0, 0, 0, 0.33f), allowAlpha = true).json("Custom tag background colour").childOf(::customTag)
    private val customTagShadow by switch("Shadow").json("Custom tag shadow").childOf(::customTag)
    private val heightOffset by slider("Height offset", 0.0, -2.2, 1.0, 0.1).childOf(::customTag)
    private val distanceText by switch("Distance").childOf(::customTag)
    private val customCol by switch("Custom colour").childOf(::distanceText)
    private val distanceColour by colourPicker("Colour", Colour.WHITE).json("Distance colour").childOf(::customCol)

    private val vanillaTagDropDown by text("Vanilla nametags")
    @JvmStatic val customBg by switch("Custom background").childOf(::vanillaTagDropDown)
    @JvmStatic val bgColour by colourPicker("Background colour", Colour.RGB(0, 0, 0, 0.33f), allowAlpha = true).childOf(::customBg)
    @JvmStatic val shadow by switch("Shadow").childOf(::vanillaTagDropDown)

    init {
        on<RenderEvent.World> {
            if (!shouldCancelTag) return@on
            val pos = player.renderPos
            playerEntitiesNoSelf.forEach { entity ->
                val name = entity.displayName?.let { displayName ->
                    when {
                        inDungeons -> dungeonTeammatesNoSelf.firstOrNull { it.name == entity.name.string }
                            ?.let { literal("&${it.clazz.colourCode}${it.name}") }
                            ?: if (simpleTag) displayName.simple else displayName
                        simpleTag -> displayName.simple
                        else -> displayName
                    }
                } ?: return@forEach
                val dist = pos.distanceToSqr(entity.renderX, entity.renderY, entity.renderZ)
                if (distanceText) { // fixme
                    (name as MutableComponent)
                        .append(literal(" ${entity.distanceToCamera.toFixed(1)}")
                            .withColor((if (customCol) distanceColour else entity.colourFromDistance).rgb)
                    )
                }
                val scale = (0.5 + dist.pow(0.5) / 10.0).toFloat()

                ctx.drawText(name, entity.renderPos.add(0.0, 2.2 + heightOffset, 0.0), customTagBgColour, customTagShadow, scale, false)
            }
        }
    }

    private val Component.simple: Component // fixme
        get() = Regex("\\[\\d+]\\s+(\\S+)").find(string)?.groupValues?.get(1)?.let { name -> // "[145] aboba ♲" "[67] aloba"
        literal(name)
            .withStyle { it.withColor(style.color) }
            .append(if (string.contains("♲")) literal(" &7♲") else Component.empty())
    } ?: this

    @JvmStatic
    val shouldCancelTag get() = this.enabled && customTag && (!dungeonsOnly || inDungeons)
}