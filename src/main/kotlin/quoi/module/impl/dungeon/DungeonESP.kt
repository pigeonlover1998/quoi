package quoi.module.impl.dungeon

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.EnderMan
import net.minecraft.world.entity.player.Player
import quoi.annotations.AlwaysActive
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.EntityEvent
import quoi.api.events.RenderEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Dungeon.floor
import quoi.api.skyblock.dungeon.Dungeon.inBoss
import quoi.api.skyblock.dungeon.odonscanning.ScanUtils
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomState
import quoi.api.skyblock.location.invoke
import quoi.module.Module
import quoi.module.impl.misc.Test
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.module.settings.group.SettingGroup.Companion.childOf
import quoi.module.settings.group.SettingGroup.Companion.json
import quoi.utils.EntityUtils.getEntities
import quoi.utils.EntityUtils.getEntity
import quoi.utils.EntityUtils.interpolatedBox
import quoi.utils.Scheduler.scheduleLoop
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.equalsOneOf
import quoi.utils.removeIf
import quoi.utils.render.drawStyledBox

@AlwaysActive
object DungeonESP : Module(
    "Dungeon ESP",
    desc = "Highlights various dungeon entities.",
    area = Island.Dungeon(inClear = true)
) {
    private val teammateClassGlow by switch("Teammate class glow", true, desc = "Highlights dungeon teammates based on their class colour.")
    private val starEsp by switch("Starred mobs")

    private val starHighlight = highlight(colour = null, fillColour = null).childOf(::starEsp)

    private val colourDropdown by text("Colours").childOf(::starEsp)
    private val colourStar by colourPicker("Star", Colour.RED, true, "ESP color for star mobs.").childOf(::colourDropdown)
    private val colourSA by colourPicker("Shadow assassin", Colour.RED, true, "ESP color for shadow assassins.").childOf(::colourDropdown)
    private val colourBat by colourPicker("Bat", Colour.RED, true, "ESP color for bats.").childOf(::colourDropdown)

    private val fillDropdown by text("Fill colours").childOf(::starEsp).visibleIf { starHighlight.style == "Filled box" }
    private val colourStarFill by colourPicker("Star", Colour.RED.withAlpha(60), true, "ESP color for star mobs.").json("Star fill").childOf(::fillDropdown)
    private val colourSAFill by colourPicker("Shadow assassin", Colour.RED.withAlpha(60), true, "ESP color for shadow assassins.").json("Shadow assassin fill").childOf(::fillDropdown)
    private val colourBatFill by colourPicker("Bat", Colour.RED.withAlpha(60), true, "ESP color for bats.").json("Bat fill").childOf(::fillDropdown)

    private val bossEsp by switch("Wither boss")
    private val bossHighlight = highlight("Style", aabbOffset = true).json("Boss style").childOf(::bossEsp)

    var currentEntities = mutableMapOf<Int, EspMob>()
        private set

    init {
        scheduleLoop(10) { // maybe move to dungeon utils
            if (/*!enabled || !starEsp || */!Dungeon.inClear/* || style.selected == "Glow"*/) return@scheduleLoop
            updateEntities()
            Test.collectMobs()
        }

        on<WorldEvent.Change> {
            currentEntities.clear()
        }

        on<RenderEvent.World> {
            currentEntities.removeIf { _, mob ->
                val entity = mob.entity

                if (entity.isDeadOrDying || entity.isRemoved) return@removeIf true
                if (!enabled || !starEsp) return@removeIf false

                starHighlight.draw(ctx, entity.interpolatedBox, mob.colour, mob.fillColour)
                false
            }

            if (enabled && bossEsp && inBoss && floor?.floorNumber == 7) getEntities<WitherBoss>()
                .filter { it.isWitherBoss }
                .forEach { bossHighlight.draw(ctx, it.interpolatedBox) }
        }

        on<EntityEvent.ForceGlow> {
            if (!enabled) return@on
            getTeammateColour(entity)?.let { glowColour = it }

            if (starEsp) {
                getColour(entity)?.let {
                    starHighlight.draw(this, it.first)
                    return@on
                }

                currentEntities[entity.id]?.let {
                    starHighlight.draw(this, it.colour)
                }
            }

            if (bossEsp && inBoss && floor?.floorNumber == 7 && entity.isWitherBoss) {
                bossHighlight.draw(this)
            }
        }
    }

    private fun handleStand(stand: ArmorStand) {
        val name = stand.customName?.string ?: return
        if ("✯" !in name && !name.endsWith("§c❤")) return

        val offset = if (name.noControlCodes.contains("withermancer", true)) 3 else 1
        val realId = stand.id - offset

        stand.level().getEntity(realId)?.takeIf { it is LivingEntity && it !is ArmorStand }?.let {
            addMob(it as LivingEntity, colourStar, colourStarFill)
            return
        }


        stand.level().getEntities(stand, stand.boundingBox.move(0.0, -1.0, 0.0)) {
            it !is ArmorStand && it is LivingEntity && it != player
        }.firstOrNull()?.let {
            addMob(it as LivingEntity, colourStar, colourStarFill)
        }
    }

    private fun getColour(entity: Entity) = when (entity) {
        is Bat if (entity.maxHealth.equalsOneOf(100f, 200f, 400f, 800f)) -> colourBat to colourBatFill
//        is EnderMan if (entity.name.string == "Dinnerbone") -> {
//            colourStar to colourStarFill
//        }
        is EnderMan if (entity.name.string == "Dinnerbone") -> {
            val stand = getEntity(entity.id + 1) as? ArmorStand

            if (stand?.customName?.string?.contains("✯") == true) {
                colourStar to colourStarFill
            } else null
        }
        is ArmorStand -> {
            handleStand(entity)
            null
        }
        is Player -> with(entity.name.string) {
//            if (!contains("✯")) null
            /*else*/ if (contains("Shadow Assassin")) colourSA to colourSAFill
            else if (equalsOneOf("Diamond Guy", "Lost Adventurer")) colourStar to colourStarFill
            else null
        }
        else -> null
    }

    private fun updateEntities() {
        getEntities<LivingEntity>().forEach { entity ->
            if (currentEntities.containsKey(entity.id)) return@forEach
            getColour(entity)?.let { (colour, fillColour) ->
                addMob(entity, colour, fillColour)
            }
        }
    }

    private fun addMob(entity: LivingEntity, col: Colour, fill: Colour) {
        val room = ScanUtils.getRoomFromPos(entity.x, entity.z)
        currentEntities[entity.id] = EspMob(entity, col, fill, room)
    }

    private fun getTeammateColour(entity: Entity): Colour? {
        if (!teammateClassGlow || !Dungeon.inDungeons || entity !is Player) return null
        return Dungeon.dungeonTeammates.find { it.name == entity.name.string }?.clazz?.colour
    }

    val OdonRoom.starredMobs: List<LivingEntity> get() {
        if (this.data.state == RoomState.GREEN) return emptyList()
        val res = ArrayList<LivingEntity>()
        for (mob in currentEntities.values) {
            if (mob.room == this && mob.entity !is Bat) res.add(mob.entity)
        }
        return res
    }

    data class EspMob(val entity: LivingEntity, val colour: Colour, val fillColour: Colour, val room: OdonRoom?)

    private val Entity.isWitherBoss get() = this is WitherBoss && !this.isInvisible && this.invulnerableTicks != 800
}