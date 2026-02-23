package quoi.module.impl.player

import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.dsl.at
import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.dsl.size
import quoi.api.abobaui.dsl.withScale
import quoi.api.abobaui.elements.Element
import quoi.api.abobaui.elements.impl.Block
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.colour.Colour
import quoi.api.skyblock.Location.inSkyblock
import quoi.api.skyblock.SkyblockPlayer
import quoi.api.skyblock.SkyblockPlayer.DEF_REGEX
import quoi.api.skyblock.SkyblockPlayer.HP_REGEX
import quoi.api.skyblock.SkyblockPlayer.MANA_REGEX
import quoi.api.skyblock.SkyblockPlayer.MANA_USAGE_REGEX
import quoi.api.skyblock.SkyblockPlayer.OVERFLOW_REGEX
import quoi.api.skyblock.SkyblockPlayer.SALVATION_REGEX
import quoi.api.skyblock.SkyblockPlayer.SECRETS_REGEX
import quoi.api.skyblock.SkyblockPlayer.STACKS_REGEX
import quoi.api.skyblock.SkyblockPlayer.currentSecrets
import quoi.api.skyblock.SkyblockPlayer.maxSecrets
import quoi.api.skyblock.dungeon.Dungeon.inDungeons
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.DropdownSetting
import quoi.utils.commas
import quoi.utils.ui.hud.ResizableHud
import quoi.utils.ui.hud.TextHud
import quoi.utils.ui.hud.setting
import quoi.utils.ui.rendering.NVGRenderer.minecraftFont
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import kotlin.reflect.KProperty0

object PlayerDisplay : Module(
    "Player Display",
    desc = "Hud displays for various skyblock stats."
) {

    private const val BAR_WIDTH = 150f
    private const val BAR_HEIGHT = 14f

    private val hideDropdown by DropdownSetting("Hide dropdown").collapsible()

    private val hideHealth by BooleanSetting("Health", desc = "Hides player health bar.").json("Hide health").withDependency(hideDropdown)
    private val hideAbsorption by BooleanSetting("Absorption", desc = "Hides absorption health bar.").withDependency(hideDropdown)
    private val hideMountHealth by BooleanSetting("Mount health", desc = "Hides health bar of the mounted entity.").withDependency(hideDropdown)
    private val hideRegenBounce by BooleanSetting("Regeneration bounce", desc = "Stops hearts from bouncing when regenerating.").withDependency(hideDropdown)
    private val hideArmour by BooleanSetting("Armour", desc = "Hides armour bar.").json("Hide armour").withDependency(hideDropdown)
    private val hideHunger by BooleanSetting("Hunger", desc = "Hides hunger bar.").json("Hide hunger").withDependency(hideDropdown)

    private val healthDropdown by DropdownSetting("Health dropdown").collapsible()

    private val health by text(
        name = "Health",
        defaultColour = Colour.MINECRAFT_RED,
        text = { "${(SkyblockPlayer.health + SkyblockPlayer.absorption).commas()}/${SkyblockPlayer.maxHealth.commas()}" },
        previewText = { "1,200/10,900" }
    ).withDependency(healthDropdown)

    private val healthBar by bar(
        name = "Health bar",
        defaultColour = Colour.MINECRAFT_RED,
        current = { SkyblockPlayer.health },
        max = { SkyblockPlayer.maxHealth }
    ).withDependency(healthDropdown)

    private val effectiveHealth by text(
        name = "Effective health",
        defaultColour = Colour.MINECRAFT_DARK_GREEN,
        text = { SkyblockPlayer.effectiveHealth.commas() },
        previewText = { "54,879" }
    ).withDependency(healthDropdown)

    private val manaDropdown by DropdownSetting("Mana dropdown").collapsible()

    private val mana by text(
        name = "Mana",
        defaultColour = Colour.MINECRAFT_BLUE,
        text = { "${SkyblockPlayer.mana.commas()}/${SkyblockPlayer.maxMana.commas()}" },
        previewText = { "1,300/10,900" }
    ).withDependency(manaDropdown)

    private val manaBar by bar(
        name = "Mana bar",
        defaultColour = Colour.MINECRAFT_BLUE,
        current = { SkyblockPlayer.mana },
        max = { SkyblockPlayer.maxMana }
    ).withDependency(manaDropdown)

    private val manaUsage by text(
        name = "Mana usage",
        text = { SkyblockPlayer.manaUsage },
        previewText = { "§b-50 Mana (§6Speed Boost§b)" },
        visibility = { SkyblockPlayer.manaUsage.isNotEmpty() }
    ).withDependency(manaDropdown)

    private val overflowMana by text(
        name = "Overflow mana",
        defaultColour = Colour.RGB(0, 170, 170),
        text = { "${SkyblockPlayer.overflowMana.commas()}ʬ" },
        previewText = { "600ʬ" },
        visibility = { SkyblockPlayer.overflowMana != 0 }
    ).withDependency(manaDropdown)

    private val otherDropdown by DropdownSetting("Other").collapsible()

    private val defence by text(
        name = "Defence",
        defaultColour = Colour.MINECRAFT_GREEN,
        text = { SkyblockPlayer.defence.commas() },
        previewText = { "10,000" }
    ).withDependency(otherDropdown)

    private val speedPercent by BooleanSetting("Render speed as a percentage").withDependency(otherDropdown)
    private val speed by text(
        name = "Speed",
        text = { if (speedPercent) "${SkyblockPlayer.speed}%" else "✦${SkyblockPlayer.speed}" },
        previewText = { if (speedPercent) "500%" else "✦500" },
        settings = listOf(::speedPercent)
    ).withDependency(otherDropdown)

    private val stacks by text(
        name = "Crimson stacks",
        text = { SkyblockPlayer.stacks },
        previewText = { "10ᝐ" },
        visibility = { SkyblockPlayer.stacks.isNotEmpty() }
    ).withDependency(otherDropdown)

    private val salvation by text(
        name = "Salvation",
        text = { "T${SkyblockPlayer.salvation}!" },
        previewText = { "T3!" },
        visibility = { SkyblockPlayer.salvation != 0 }
    ).withDependency(otherDropdown)

    private val sbaStyle: Boolean by BooleanSetting("SBA secrets style").withDependency(otherDropdown)
    private val secrets by TextHud("Secret display", Colour.MINECRAFT_GRAY) {
        visibleIf { inDungeons }

        val displayText = {
            val colour = when (currentSecrets / maxSecrets.toDouble()) {
                in 0.0..0.5 -> "§c"
                in 0.5..0.75 -> "§e"
                else -> "§a"
            }
            if (currentSecrets > -1) "$colour$currentSecrets§r/$colour$maxSecrets" else "Unknown"
        }

        if (!sbaStyle) {
            textSupplied(
                supplier = { "${displayText()} Secrets" },
                colour = colour,
                font = minecraftFont,
                size = 18.px,
            ).shadow = shadow
            return@TextHud
        }

        row {
            object : Element(size(32.px, 32.px)) {
                override fun draw() {
                    withScale {
                        ctx.pose().scale(2f, 2f)
                        ctx.renderItem(ItemStack(Blocks.CHEST), 0, 0)
                    }
                }
            }.add()

            column(gap = 5.px) {
                text(
                    string = "Secrets",
                    colour = colour,
                    font = minecraftFont,
                    size = 18.px,
                ).shadow = shadow
                textSupplied(
                    supplier = displayText,
                    colour = colour,
                    font = minecraftFont,
                    size = 18.px,
                    pos = at(Centre)
                ).shadow = shadow
            }
        }

    }.withSettings(::sbaStyle).setting().withDependency(otherDropdown)

    @JvmStatic
    fun modifyActionBar(text: Component): Component {
        if (!this.enabled) return text

        return Component.literal(
            listOf(
                health to HP_REGEX,
                mana to MANA_REGEX,
                overflowMana to OVERFLOW_REGEX,
                defence to DEF_REGEX,
                stacks to STACKS_REGEX,
                salvation to SALVATION_REGEX,
                manaUsage to MANA_USAGE_REGEX,
                secrets to SECRETS_REGEX
            ).fold(text.string) { acc, (hud, regex) ->
                if (hud.enabled) acc.replace(regex, "") else acc
            }.trim()
        )
    }

    enum class HudType {
        HEALTH, ABSORPTION, MOUNT_HEALTH, REGEN_BOUNCE,
        ARMOUR, FOOD,
    }

    @JvmStatic
    fun shouldCancelHud(type: HudType): Boolean {
        if (!this.enabled || !inSkyblock) return false
        return when(type) {
            HudType.HEALTH -> hideHealth
            HudType.ABSORPTION -> hideAbsorption
            HudType.MOUNT_HEALTH -> hideMountHealth
            HudType.REGEN_BOUNCE -> hideRegenBounce
            HudType.ARMOUR -> hideArmour
            HudType.FOOD -> hideHunger
        }
    }

    /*private fun GuiGraphics.drawBar(width: Int, colour: Colour) {
        val bW = BAR_WIDTH * 2
        val bH = BAR_HEIGHT * 2
        fill(0, 0, bW, bH, Colour.RGB(35, 35, 35).rgb)
        fill(0, 0, width * 2, bH, colour.rgb)
        hollowRect(0, 0, bW, bH, 1, Colour.RGB(208, 208, 208).rgb)
    }

    fun bar(
        name: String,
        colour: () -> Colour,
        current: () -> Int,
        max: () -> Int
    ) = HudSetting(name) {
        size(BAR_WIDTH, BAR_HEIGHT)
        width { BAR_WIDTH * 2 }
        height { BAR_HEIGHT * 2 }
        visibleIf { inSkyblock }
        render {
            val fillWidth = (current().toFloat() / max().toFloat() * width).toInt()
            drawBar(fillWidth, colour.invoke())
        }
        preview {
            drawBar(50, colour.invoke())
        }
    }*/

    fun bar(
      name: String,
      defaultColour: Colour,
      current: () -> Int,
      max: () -> Int
    ) = ResizableHud(name, BAR_WIDTH, BAR_HEIGHT, defaultColour, Colour.RGB(208, 208, 208), 1f) {
        visibleIf { inSkyblock }
        ctxBlock(
            size(width, height),
            Colour.RGB(35, 35, 35),
        )

        object : Block.CtxBlock(size(width, height), colour) {
            override fun draw() {
                width = if (preview) 50f else (current().toFloat() / max().toFloat() * BAR_WIDTH)
                super.draw()
            }
        }.add()

        ctxBlock( // idkman
            size(width, height),
            Colour.TRANSPARENT
        ).outline(outline, thickness)
    }.setting()

    /*fun text(
        name: String,
        colour: () -> Colour = { Colour.WHITE },
        text: () -> String,
        previewText: () -> String,
        visibility: () -> Boolean = { true }
    ) = HudSetting(name) {
//        size(previewText())
        width { previewText().width() }
        visibleIf { inSkyblock && visibility() }
        render { drawString(text(), 0, 0, colour.invoke().rgb) }
        preview { drawString(previewText(), 0, 0, colour.invoke().rgb) }
    }*/

    fun text(
        name: String,
        defaultColour: Colour = Colour.WHITE,
        text: () -> String,
        previewText: () -> String,
        visibility: () -> Boolean = { true },
        settings: List<KProperty0<*>> = emptyList()
    ) = TextHud(name, defaultColour) {
        visibleIf { inSkyblock && visibility() }
        textSupplied(
            supplier = if (preview) previewText else text,
            colour = colour,
            font = minecraftFont,
            size = 18.px,
        ).shadow = shadow
    }.withSettings(*settings.toTypedArray()).setting()
}