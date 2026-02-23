package quoi.module.impl.render

import quoi.api.colour.Colour
import quoi.api.events.RenderEvent
import quoi.api.skyblock.dungeon.Dungeon.dungeonTeammatesNoSelf
import quoi.api.skyblock.dungeon.Dungeon.inDungeons
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.ColourSetting
import quoi.module.settings.impl.DropdownSetting
import quoi.module.settings.impl.NumberSetting
import quoi.utils.ChatUtils.literal
import quoi.utils.render.drawText
import quoi.utils.EntityUtils.colourFromDistance
import quoi.utils.EntityUtils.distanceToCamera
import quoi.utils.EntityUtils.playerEntitiesNoSelf
import quoi.utils.EntityUtils.renderPos
import quoi.utils.EntityUtils.renderX
import quoi.utils.EntityUtils.renderY
import quoi.utils.EntityUtils.renderZ
import quoi.utils.StringUtils.toFixed
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import kotlin.math.pow

object NameTags : Module(
    "Name Tags"
) {
    private val customTagDropdown by DropdownSetting("Custom nametags").collapsible()
    private val customTag by BooleanSetting("Toggle").json("Custom nametags toggle").withDependency(customTagDropdown)
    private val dungeonsOnly by BooleanSetting("Dungeons only").withDependency(customTagDropdown) { customTag }
    private val simpleTag by BooleanSetting("Simple tag").withDependency(customTagDropdown) { customTag }
    private val customTagBgColour by ColourSetting("Background colour", Colour.RGB(0, 0, 0, 0.33f), allowAlpha = true).json("Custom tag background colour").withDependency(customTagDropdown) { customTag }
    private val customTagShadow by BooleanSetting("Shadow").json("Custom tag shadow").withDependency(customTagDropdown) { customTag }
    private val heightOffset by NumberSetting("Height offset", 0.0, -2.2, 1.0, 0.1).withDependency(customTagDropdown) { customTag }
    private val distanceText by BooleanSetting("Distance").withDependency(customTagDropdown) { customTag }
    private val distCols by BooleanSetting("Distance colours", true).withDependency(customTagDropdown) { customTag && distanceText }
    private val distanceColour by ColourSetting("Distance colour", Colour.WHITE).json("Distance colour").withDependency(customTagDropdown) { customTag && distanceText && !distCols }

    private val vanillaTagDropDown by DropdownSetting("Vanilla nametags").collapsible()
    @JvmStatic val customBg by BooleanSetting("Custom background").withDependency(vanillaTagDropDown)
    @JvmStatic val bgColour by ColourSetting("Background colour", Colour.RGB(0, 0, 0, 0.33f), allowAlpha = true).withDependency(vanillaTagDropDown) { customBg }
    @JvmStatic val shadow by BooleanSetting("Shadow").withDependency(vanillaTagDropDown)

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
                if (distanceText) {
                    (name as MutableComponent)
                        .append(literal(" ${entity.distanceToCamera.toFixed(1)}")
                            .withColor((if (distCols) entity.colourFromDistance else distanceColour).rgb)
                    )
                }
                val scale = (0.5 + dist.pow(0.5) / 10.0).toFloat()

                ctx.drawText(name, entity.renderPos.add(0.0, 2.2 + heightOffset, 0.0), customTagBgColour, customTagShadow, scale, false)
            }
        }
    }

    private val Component.simple: Component
        get() = Regex("\\[\\d+]\\s+(\\S+)").find(string)?.groupValues?.get(1)?.let { name -> // "[145] aboba ♲" "[67] aloba"
        literal(name)
            .withStyle { it.withColor(style.color) }
            .append(if (string.contains("♲")) literal(" &7♲") else Component.empty())
    } ?: this

    @JvmStatic
    val shouldCancelTag get() = this.enabled && customTag && (!dungeonsOnly || inDungeons)
}