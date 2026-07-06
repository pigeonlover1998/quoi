package quoi.module.impl.misc

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import quoi.api.abobaui.dsl.radius
import quoi.api.abobaui.dsl.size
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.commands.internal.BaseCommand
import quoi.api.events.DungeonEvent
import quoi.api.events.KeyEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.api.pathfinding.impl.WalkPathfinder
import quoi.api.skyblock.location.Location
import quoi.api.skyblock.dungeon.Dungeon
import quoi.module.Module
import quoi.module.impl.render.clickgui.ClickGui
import quoi.utils.*
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.formatTime
import quoi.utils.StringUtils.toFixed
import quoi.api.skyblock.dungeon.odonscanning.ScanUtils
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomType
import quoi.config.Config
import quoi.module.impl.dungeon.DungeonESP
import quoi.module.impl.dungeon.DungeonESP.starredMobs
import quoi.module.impl.dungeon.autoclear.MobCluster
import quoi.module.impl.dungeon.autoclear.MobClusterer
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.group.ToggleableGroup
import quoi.module.settings.impl.MapSetting
import quoi.utils.WorldUtils.blocksAtFeet
import quoi.utils.WorldUtils.nearbyBlocks
import quoi.utils.WorldUtils.solid
import quoi.utils.WorldUtils.state
import quoi.utils.WorldUtils.ticksUntilCollision
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawLine
import quoi.utils.skyblock.player.interact.AuraAction
import quoi.utils.skyblock.player.interact.AuraManager
import quoi.utils.skyblock.player.ContainerUtils
import quoi.utils.skyblock.player.MovementUtils.moveTo
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.RotationUtils.resetRotation
import quoi.utils.skyblock.player.RotationUtils.rotateSilently
import quoi.utils.ui.textPair
import kotlin.collections.mutableListOf

object Test : Module("Test", desc = "Dev module for testing.") {

    val testGroup = TestGroup(this)
    val auraDebug by switch("Aura debug")
    val uiDebug by switch("UI debug").onValueChanged { old, new -> ClickGui.reopen() }
    val reopen by button("Reopen") { ClickGui.reopen() }

    private val showX by switch("Show X", true)
    private val showY by switch("Show Y", true)
    private val showZ by switch("Show Z", true)

    private val coords = setOf(
        Data("x:", { (player.x).toFixed() }, { showX }),
        Data("y:", { (player.y).toFixed() }, { showY }),
        Data("z:", { (player.z).toFixed() }, { showZ })
    )

    private val texthudtest by textHud("coords") {
        column {
            coords.forEach { (name, coord, enabled) ->
                if (!enabled()) return@forEach
                textPair(
                    string = name,
                    supplier = { if (preview) 0.0 else coord() },
                    labelColour = Colour.WHITE,
                    valueColour = colour,
                    shadow = shadow,
                    font = font,
                )
            }
        }
    }.withSettings(::showX, ::showY, ::showZ)
    .setting()

    private val resizableTest by resizableHud("resizable2", 75f, 7f, outline = Colour.RED) {
        block(
            size(width, height),
            colour = colour,
            5.radius()
        ).outline(outline, thickness)
    }.setting()

    private val hypixel by switch("Hypixel", true)
    private val zapto by switch("Zapto", true)
    private val inSB by switch("In skyblock", true)
    private val lobby by switch("Lobby", true)
//    private val inDung by BooleanSetting("In dungeon", true)
    private val area_ by switch("Area", true)
    private val subarea_ by switch("Subarea", true)
    private val boss by switch("Boss", true)
    private val floor by switch("Floor", true)
    private val p3Section by switch("P3 section", true)
    private val p3Players by switch("P3 players", true)
    private val container by switch("Container", true)

    private val debug by textHud("debug") {
        column {
            debugData.forEach { (name, value, enabled) ->
                if (!enabled()) return@forEach
                textPair(
                    string = "$name:",
                    supplier = { value() },
                    labelColour = Colour.WHITE,
                    valueColour = colour,
                    shadow = shadow,
                    font = font
                )
                if (name == "   Gate" && p3Players) Dungeon.dungeonTeammates.forEach { player ->
                    textPair(
                        string = "   ${player.name}:",
                        supplier = { "${player.p3Stats.terminals}T | ${player.p3Stats.levers}L | ${player.p3Stats.devices}D" },
                        labelColour = player.colour,
                        valueColour = colour,
                        shadow = shadow,
                        font = font
                    )
                }
            }
        }
    }.withSettings(::hypixel, ::inSB, /*::inDung,*/::lobby, ::area_, ::subarea_, ::boss, ::floor, ::p3Section, ::p3Players, ::container
    ).setting()

    private val collectData by switch("Collect mob data")
    private val mobData by MapSetting("mob_data", mutableMapOf<String, MutableList<DungeonMob>>())

    init {
        val command = BaseCommand("quoitest")

        command.sub("testblock") {
            val a = player.blocksAtFeet { _, state ->
                state.block == Blocks.TERRACOTTA
            }.firstOrNull()?.first ?: return@sub modMessage("no terracotta")

            val safePos =
                player.position().nearbyBlocks(2.0f) { it.state.block != Blocks.TERRACOTTA && it.solid }
                    .minByOrNull { it.center.distanceToSqr(player.position()) }?.center
                    ?: return@sub modMessage("no safe blocks")

            val awayDir = safePos.subtract(a.center).normalize()
            val safe = safePos.add(awayDir.scale(0.3))

            modMessage(safe)

            player.moveTo(safe)
        }

        command.sub("silentRot") { yaw: Float, pitch: Float ->
            player.rotateSilently(yaw, pitch)
        }

        command.sub("silentreset") {
            player.resetRotation()
        }

        command.sub("blockaura") {
            mc.hitResult?.let {
                if (it !is BlockHitResult) return@let
                AuraManager.interactBlock(it.blockPos)
            }
        }

        command.sub("entityaura") {
            val entity = EntityUtils.entities.filter { it != player }.minByOrNull { it.distanceTo(player) } ?: return@sub
            AuraManager.interactEntity(entity, action = AuraAction.INTERACT_AT)
        }

        command.sub("mineblock") { custom: Boolean? ->
//            EventListenerTests.runAll()
            mc.hitResult?.let {
                if (it !is BlockHitResult) return@let
                AuraManager.breakBlock(it.blockPos, custom = custom ?: false)
            }
        }

        command.sub("transpath") {
//            val room = Dungeon.currentRoom ?: return@sub modMessage("room is null")
//            pathToMobs(player.position(), room)

            val goal = BlockPos(-963, 68, 18477)
            path = WalkPathfinder.findPath(player.position(), goal, feedback = true)
//            path?.let { player.moveTo(it) }
            player.moveTo(goal)
        }

        command.sub("spawnstarred") {
            ChatUtils.command("/summon zombie ~ ~ ~ {NoAI:1b, PersistenceRequired:1b, Passengers:[{id:\"minecraft:armor_stand\", Marker:1b, CustomNameVisible:1b, CustomName:'✯ \\u00A7c❤', NoGravity:1b}]}")
        }

        command.sub("findclusters") {
            val room = Dungeon.currentRoom ?: return@sub modMessage("room i snull")
            clusters = MobClusterer.getOrderedClusters(player.position(), room.starredMobs)
        }

        on<RenderEvent.World> {
            if (clusters != null) {
                val clusters = clusters!!

                clusters.forEach { (seed, mobs) ->
                    ctx.drawFilledBox(seed.aabb.inflate(0.099), colour = Colour.WHITE.withAlpha(150), depth = true)
                    mobs.forEach {
                        ctx.drawFilledBox(it.position().aabb(0.5).setMinY(seed.y + 1.0), colour = Colour.PINK.withAlpha(100), depth = true)
                    }
                }
            }

            if (path != null) {
                val path = path!!
//                path.forEach {
//                    ctx.drawFilledBox(it.aabb, colour = Colour.ORANGE.withAlpha(150))
//                }
                ctx.drawLine(path, colour = Colour.WHITE, depth = false)
            }

//            val simulation = PlayerSimulation.simulation
//            val points = mutableListOf<Vec3>()
//
//            points.add(player.position())
//
//            repeat(20) {
//                val snapshot = simulation.getSnapshotAt(it + 1)
//                points.add(snapshot.pos)
//            }
//
//            ctx.drawLine(points, Colour.WHITE, depth = true)
        }

        on<DungeonEvent.Room.Scan> {
            if (!collectData) return@on
            if (room.data.type == RoomType.NORMAL && !mobData.containsKey(room.name)) {
                modMessage("!!! SCAN &e${room.name} !!!")
                PlayerUtils.setTitle("SCAN &e${room.name}", playSound = true)
                modMessage(mobData.keys.size)
            }
        }

        on<WorldEvent.Change> {
            collectedMobs.clear()
            currRooms.clear()
        }

//        on<TickEvent.Start> {
////            println("test")
//        }

//        on<KeyEvent.Input>(Priority.LOW) {
//            if (!input.moving || player.blocksAtFeet(0.0, fire).any()) return@on
//            val ticks = ticksUntilCollision(BlockPos(-1900, 66, -1900)) ?: return@on
//            if (ticks <= 1) {
//                input.forward = false
//                input.backward = false
//                input.left = false
//                input.right = false
//                input.shift = true
//            }
//        }

        command.register()
    }

    private val fire: (BlockPos, BlockState) -> Boolean = { _, state ->
        state.block == Blocks.FIRE
    }

    private var clusters: List<MobCluster>? = null
    private var path: List<Vec3>? = null


    private val collectedMobs = mutableMapOf<Int, DungeonMob>()
    private val currRooms = mutableSetOf<String>()
    // missing cobble wall pillar, knight // nvm need all again..
    fun collectMobs() {
        if (!enabled || !collectData) return
        EntityUtils.getEntities<LivingEntity>().forEach { entity ->

            val starred = DungeonESP.currentEntities.containsKey(entity.id)

            val existing = collectedMobs[entity.id]
            if (existing != null) {
                if (!existing.starred && starred) {
                    existing.starred = true
                }
                return@forEach
            }

            if (entity is ArmorStand || entity == player || entity is Bat) return@forEach

            val room = ScanUtils.getRoomFromPos(entity.x, entity.z) ?: return@forEach
            val roomName = room.name

            val already = mobData.containsKey(roomName)
            val collecting = currRooms.contains(roomName)

            if (already && !collecting) {
                collectedMobs[entity.id] = DungeonMob(
                    name = entity.name.string,
                    starred = starred,
                    pos = intArrayOf()
                )
                return@forEach
            }

            if (!collecting) {
                currRooms.add(roomName)
            }


            val pos = room.getRelativeCoords(entity.blockPosition())

            val data = DungeonMob(
                name = entity.name.string,
                starred = starred,
                pos = intArrayOf(pos.x, pos.y, pos.z)
            )

            mobData.getOrPut(roomName) { mutableListOf() }.add(data)

            collectedMobs[entity.id] = data
        }
        Config.save()
    }

    private val debugData = setOf(
        Data("Hypixel", { Location.onHypixel }, { hypixel }),
        Data("Zapto", { Location.onZapto }, { zapto }),
        Data("Skyblock", { Location.inSkyblock }, { inSB }),
        Data("Lobby", { Location.currentServer ?: "None" }, { lobby }),
//        Data("Dungeon", { Dungeon.inDungeons }, { inDung }),
        Data("Area", { Location.currentArea }, { area_ }),
        Data("Subarea", { Location.subarea ?: "None" }, { subarea_ }),
        Data("Boss", { Dungeon.inBoss }, { boss }),
        Data("Floor", { Dungeon.floor ?: "None" }, { floor }),
        Data("P3 Section", { "${Dungeon.p3Section.name} || ${Dungeon.getP3Section().name} }" }, { p3Section }),
        Data("   Duration", { "${formatTime(Dungeon.p3Section.getDuration())} | ${formatTime(Dungeon.p3Section.getDurationTicks() * 50)}" }, { p3Section } ),
        Data("   Terminals", { "${Dungeon.p3Section.terminals}/${Dungeon.p3Section.reqTerminals}" }, { p3Section }),
        Data("   Levers", { "${Dungeon.p3Section.levers}/2" }, { p3Section }),
        Data("   Device", { "${Dungeon.p3Section.device}" }, { p3Section }),
        Data("   Gate", { Dungeon.p3Section.gate }, { p3Section }),
        Data("Container", { "${mc.screen != null} | ${ContainerUtils.containerId}" }, { container })
    )
    private data class Data(val name: String, val value: () -> Any?, val enabled: () -> Boolean)

    private class DungeonMob(val name: String, var starred: Boolean, val pos: IntArray)
}

class TestGroup(module: Module) : ToggleableGroup(module, "Test group", subarea = "carnival") {
    val test by switch("test")
    val text by switch("text").childOf(::test)

    init {
        on<TickEvent.Start> {
            println("if you see this the group is enabled")
            if (test) println("   this is a test2")
        }
    }
}