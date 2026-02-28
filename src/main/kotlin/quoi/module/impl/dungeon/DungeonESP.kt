package quoi.module.impl.dungeon

import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.EntityEvent
import quoi.api.events.RenderEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.invoke
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.*
import quoi.utils.EntityUtils.entities
import quoi.utils.EntityUtils.interpolatedBox
import quoi.utils.Scheduler.scheduleLoop
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.equalsOneOf
import quoi.utils.render.drawStyledBox
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.EnderMan
import net.minecraft.world.entity.player.Player
import quoi.utils.ChatUtils.modMessage

object DungeonESP : Module(
    "Dungeon ESP",
    desc = "Highlights various dungeon entities.",
    area = Island.Dungeon(inClear = true)
) {
    private val teammateClassGlow by BooleanSetting("Teammate class glow", true, desc = "Highlights dungeon teammates based on their class colour.")
    private val starEsp by BooleanSetting("Starred mobs")

    private val depth by BooleanSetting("Depth check").withDependency { starEsp }
    private val style by SelectorSetting("Style", "Box", arrayListOf("Box", "Filled box", "Glow", "2D"), desc = "Esp render style to be used.").withDependency { starEsp }
    private val thickness by NumberSetting("Thickness", 4, 1, 8, 1).withDependency { starEsp }
    private val sizeOffset by NumberSetting("Size offset", 0.0, -1.0, 1.0, 0.05, desc = "Changes box size offset.").withDependency { style.selected.equalsOneOf("Box", "Filled box") && starEsp }

    private val colourDropdown by DropdownSetting("Colours").collapsible()
    private val colourStar by ColourSetting("Star", Colour.RED, true, "ESP color for star mobs.").withDependency(colourDropdown) { starEsp }
    private val colourSA by ColourSetting("Shadow assassin", Colour.RED, true, "ESP color for shadow assassins.").withDependency(colourDropdown) { starEsp }
    private val colourBat by ColourSetting("Bat", Colour.RED, true, "ESP color for bats.").withDependency(colourDropdown) { starEsp }

    private val fillDropdown by DropdownSetting("Fill colours").collapsible().withDependency { style.selected == "Filled box" && starEsp }
    private val colourStarFill by ColourSetting("Star", Colour.RED.withAlpha(60), true, "ESP color for star mobs.").json("Star fill").withDependency(fillDropdown) { style.selected == "Filled box" && starEsp }
    private val colourSAFill by ColourSetting("Shadow assassin", Colour.RED.withAlpha(60), true, "ESP color for shadow assassins.").json("Shadow assassin fill").withDependency(fillDropdown) { style.selected == "Filled box" && starEsp }
    private val colourBatFill by ColourSetting("Bat", Colour.RED.withAlpha(60), true, "ESP color for bats.").json("Bat fill").withDependency(fillDropdown) { style.selected == "Filled box" && starEsp }

    private var currentEntities = mutableSetOf<EspMob>()

    init {
        scheduleLoop(10) {
            if (!enabled || !starEsp || !Dungeon.inClear || style.selected == "Glow") return@scheduleLoop // todo do something about this..
            updateEntities()
        }

        on<WorldEvent.Change> {
            currentEntities.clear()
        }

        on<RenderEvent.World> {
            if (!starEsp) return@on
            currentEntities.removeIf { (entity, colour, fillColour) ->
                if (entity.isDeadOrDying || entity.isRemoved) return@removeIf true
                val aabb = entity.interpolatedBox.inflate(sizeOffset, 0.0, sizeOffset)
                ctx.drawStyledBox(style.selected, aabb, colour, fillColour, thickness.toFloat(), depth)
                false
            }
        }

        on<EntityEvent.ForceGlow> {
            getTeammateColour(entity)?.let { glowColour = it }
            if (!starEsp|| style.selected != "Glow") return@on

            getColour(entity)?.let {
                glowColour = it.first
                return@on
            }

            if (currentEntities.any { it.entity == entity }) glowColour = colourStar
        }
    }

    private fun handleStand(stand: ArmorStand) {
        val name = stand.customName?.string ?: return
        if ("✯" !in name && !name.endsWith("§c❤")) return

        val offset = if (name.noControlCodes.contains("withermancer", true)) 3 else 1
        val realId = stand.id - offset

        stand.level().getEntity(realId)?.takeIf { it is LivingEntity && it !is ArmorStand }?.let {
            currentEntities.add(EspMob(it as LivingEntity, colourStar, colourStarFill))
            return
        }


        stand.level().getEntities(stand, stand.boundingBox.move(0.0, -1.0, 0.0)) {
            it !is ArmorStand && it is LivingEntity && it != player
        }.firstOrNull()?.let {
            currentEntities.add(EspMob(it as LivingEntity, colourStar, colourStarFill))
        }
    }

    private fun getColour(entity: Entity) = when (entity) {
        is Bat if (entity.maxHealth.equalsOneOf(100f, 200f, 400f, 800f)) -> colourBat to colourBatFill
        is EnderMan if (entity.name.string.noControlCodes == "Dinnerbone") -> {
            colourStar to colourStarFill
        }
        is ArmorStand -> {
            handleStand(entity)
            null
        }
        is Player -> with(entity.name.string) {
            if (contains("Shadow Assassin")) colourSA to colourSAFill
            else if (equalsOneOf("Diamond Guy", "Lost Adventurer")) colourStar to colourStarFill
            else null
        }
        else -> null
    }

    private fun updateEntities() {
        entities.forEach { entity ->
            if (entity !is LivingEntity) return@forEach
            if (currentEntities.any { it.entity == entity }) return@forEach
            getColour(entity)?.let { (colour, fillColour) ->
                currentEntities.add(EspMob(entity, colour, fillColour))
            }
        }
    }

    private fun getTeammateColour(entity: Entity): Colour? {
        if (!teammateClassGlow || !Dungeon.inDungeons || entity !is Player) return null
        return Dungeon.dungeonTeammates.find { it.name == entity.name?.string }?.clazz?.colour
    }

    private data class EspMob(val entity: LivingEntity, val colour: Colour, val fillColour: Colour)
}