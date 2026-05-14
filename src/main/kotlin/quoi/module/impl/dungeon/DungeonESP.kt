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
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Dungeon.floor
import quoi.api.skyblock.dungeon.Dungeon.inBoss
import quoi.api.skyblock.dungeon.odonscanning.ScanUtils
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomState
import quoi.api.skyblock.invoke
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
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

    private val depth by switch("Depth check").childOf(::starEsp)
    private val style by selector("Style", "Box", arrayListOf("Box", "Filled box", "Glow"/*, "2D"*/), desc = "Esp render style to be used.").childOf(::starEsp)
    private val thickness by slider("Thickness", 4, 1, 8, 1).childOf(::style) { it.selected.equalsOneOf("Box", "Filled box") }
    private val sizeOffset by slider("Size offset", 0.0, -1.0, 1.0, 0.05, desc = "Changes box size offset.").childOf(::style) { it.selected.equalsOneOf("Box", "Filled box") }

    private val colourDropdown by text("Colours").childOf(::starEsp)
    private val colourStar by colourPicker("Star", Colour.RED, true, "ESP color for star mobs.").childOf(::colourDropdown)
    private val colourSA by colourPicker("Shadow assassin", Colour.RED, true, "ESP color for shadow assassins.").childOf(::colourDropdown)
    private val colourBat by colourPicker("Bat", Colour.RED, true, "ESP color for bats.").childOf(::colourDropdown)

    private val fillDropdown by text("Fill colours").childOf(::starEsp).visibleIf { style.selected == "Filled box" }
    private val colourStarFill by colourPicker("Star", Colour.RED.withAlpha(60), true, "ESP color for star mobs.").json("Star fill").childOf(::fillDropdown)
    private val colourSAFill by colourPicker("Shadow assassin", Colour.RED.withAlpha(60), true, "ESP color for shadow assassins.").json("Shadow assassin fill").childOf(::fillDropdown)
    private val colourBatFill by colourPicker("Bat", Colour.RED.withAlpha(60), true, "ESP color for bats.").json("Bat fill").childOf(::fillDropdown)

    private val bossEsp by switch("Wither boss")
    private val depthBoss by switch("Depth check").json("Depth check boss").childOf(::bossEsp)
    private val styleBoss by selector("Style", "Box", arrayListOf("Box", "Filled box", "Glow"/*, "2D"*/), desc = "Esp render style to be used.").json("Style boss").childOf(::bossEsp)
    private val colourBoss by colourPicker("Colour", Colour.WHITE, desc = "Colour for the Boss ESP").json("Colour boss").childOf(::styleBoss)
    private val fillColourBoss by colourPicker("Fill colour", Colour.WHITE.withAlpha(60), allowAlpha = true, desc = "Fill colour for the Boss ESP").json("Fill colour boss").childOf(::styleBoss).visibleIf { style.selected == "Filled box" }
    private val thicknessBoss by slider("Thickness", 4, 1, 8, 1).json("Thickness boss").childOf(::styleBoss)
    private val sizeOffsetBoss by slider("Size offset", 0.0, -1.0, 1.0, 0.05, desc = "Changes box size offset.").json("Size offset boss").childOf(::styleBoss).visibleIf { style.selected.equalsOneOf("Box", "Filled box") }

    private var currentEntities = mutableMapOf<Int, EspMob>()

    init {
        scheduleLoop(10) { // maybe move to dungeon utils
            if (/*!enabled || !starEsp || */!Dungeon.inClear/* || style.selected == "Glow"*/) return@scheduleLoop
            updateEntities()
        }

        on<WorldEvent.Change> {
            currentEntities.clear()
        }

        on<RenderEvent.World> {
            currentEntities.removeIf { _, mob ->
                val entity = mob.entity

                if (entity.isDeadOrDying || entity.isRemoved) return@removeIf true

                if (enabled && starEsp && style.selected != "Glow") {
                    val aabb = entity.interpolatedBox.inflate(sizeOffset, 0.0, sizeOffset)
                    ctx.drawStyledBox(style.selected, aabb, mob.colour, mob.fillColour, thickness.toFloat(), depth)
                }
                false
            }

            if (enabled && bossEsp && inBoss && floor?.floorNumber == 7) getEntities<WitherBoss>()
                .filter { it.isWitherBoss }
                .forEach { entity ->
                    val aabb = entity.interpolatedBox.inflate(sizeOffsetBoss, 0.0, sizeOffsetBoss)
                    ctx.drawStyledBox(styleBoss.selected, aabb, colourBoss, fillColourBoss, thicknessBoss.toFloat(), depthBoss)
                }
        }

        on<EntityEvent.ForceGlow> {
            if (!enabled) return@on
            getTeammateColour(entity)?.let { glowColour = it }

            if (starEsp) {
                if (style.selected != "Glow") return@on
                if (depth && !player.hasLineOfSight(entity)) return@on

                getColour(entity)?.let {
                    glowColour = it.first
                    return@on
                }

                currentEntities[entity.id]?.let {
                    glowColour = it.colour
                }
            }

            if (bossEsp && inBoss && floor?.floorNumber == 7) {
                if (styleBoss.selected != "Glow" && !entity.isWitherBoss) return@on
                if (depthBoss && !player.hasLineOfSight(entity)) return@on
                glowColour = colourBoss
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
        return Dungeon.dungeonTeammates.find { it.name == entity.name?.string }?.clazz?.colour
    }

    val OdonRoom.starredMobs: List<LivingEntity> get() {
        if (this.data.state == RoomState.GREEN) return emptyList()
        val res = ArrayList<LivingEntity>()
        for (mob in currentEntities.values) {
            if (mob.room == this && mob.entity !is Bat) res.add(mob.entity)
        }
        return res
    }

    private data class EspMob(val entity: LivingEntity, val colour: Colour, val fillColour: Colour, val room: OdonRoom?)

    private val Entity.isWitherBoss get() = this is WitherBoss && !this.isInvisible && this.invulnerableTicks != 800
}