package quoi.module.impl.dungeon

import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.ChatEvent
import quoi.api.events.EntityEvent
import quoi.api.events.RenderEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.invoke
import quoi.module.Module
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.ColourSetting
import quoi.module.settings.impl.NumberSetting
import quoi.module.settings.impl.SelectorSetting
import quoi.utils.EntityUtils.entities
import quoi.utils.EntityUtils.interpolatedBox
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.equalsOneOf
import quoi.utils.render.drawStyledBox
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.boss.wither.WitherBoss

// Kyleen
object BossESP : Module( // todo move to dungeon esp
    "Boss ESP",
    desc = "Highlights floor seven bosses.",
    area = Island.Dungeon(7, inBoss = true)
) {
    private val depth by BooleanSetting("Depth check")
    private val style by SelectorSetting("Style", "Box", arrayListOf("Box", "Filled box", "Glow", "2D"), desc = "Esp render style to be used.")
    private val colour by ColourSetting("Colour", Colour.WHITE, desc = "Colour for the Boss ESP")
    private val fillColour by ColourSetting("Fill colour", Colour.WHITE.withAlpha(60), allowAlpha = true, desc = "Fill colour for the Boss ESP").withDependency { style.selected == "Filled box" }
    private val thickness by NumberSetting("Thickness", 4, 1, 8, 1)
    private val sizeOffset by NumberSetting("Size offset", 0.0, -1.0, 1.0, 0.05, desc = "Changes box size offset.").withDependency { style.selected.equalsOneOf("Box", "Filled box") }

    private var showWitherEsp = false

    init {
        on<RenderEvent.World> {
            if (!showWitherEsp) return@on
            entities.forEach { entity ->
                if (!entity.isWitherBoss) return@forEach
                val aabb = entity.interpolatedBox.inflate(sizeOffset, 0.0, sizeOffset)
                ctx.drawStyledBox(style.selected, aabb, colour, fillColour, thickness.toFloat(), depth)
            }
        }

        on<EntityEvent.ForceGlow> {
            if (style.selected != "Glow" && !entity.isWitherBoss) return@on
            glowColour = colour
        }

        on<ChatEvent.Packet> {
            when (message.noControlCodes) {
                "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!" -> showWitherEsp = true
                "[BOSS] Necron: All this, for nothing..." -> showWitherEsp = false
            }
        }
    }

    private val Entity.isWitherBoss get() = this is WitherBoss && !this.isInvisible && this.invulnerableTicks != 800
}