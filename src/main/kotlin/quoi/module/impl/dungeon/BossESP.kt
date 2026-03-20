package quoi.module.impl.dungeon

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.boss.wither.WitherBoss
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.ChatEvent
import quoi.api.events.EntityEvent
import quoi.api.events.RenderEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.invoke
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.EntityUtils.getEntities
import quoi.utils.EntityUtils.interpolatedBox
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.equalsOneOf
import quoi.utils.render.drawStyledBox

// Kyleen
object BossESP : Module( // todo move to dungeon esp
    "Boss ESP",
    desc = "Highlights floor seven bosses.",
    area = Island.Dungeon(7, inBoss = true)
) {
    private val depth by switch("Depth check")
    private val style by selector("Style", "Box", arrayListOf("Box", "Filled box", "Glow"/*, "2D"*/), desc = "Esp render style to be used.")
    private val colour by colourPicker("Colour", Colour.WHITE, desc = "Colour for the Boss ESP")
    private val fillColour by colourPicker("Fill colour", Colour.WHITE.withAlpha(60), allowAlpha = true, desc = "Fill colour for the Boss ESP").visibleIf { style.selected == "Filled box" }
    private val thickness by slider("Thickness", 4, 1, 8, 1)
    private val sizeOffset by slider("Size offset", 0.0, -1.0, 1.0, 0.05, desc = "Changes box size offset.").visibleIf { style.selected.equalsOneOf("Box", "Filled box") }

    private var showWitherEsp = false

    init {
        on<RenderEvent.World> {
            if (!showWitherEsp) return@on
            getEntities<WitherBoss>()
                .filter { !it.isInvisible && it.invulnerableTicks != 800 }
                .forEach { entity ->
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