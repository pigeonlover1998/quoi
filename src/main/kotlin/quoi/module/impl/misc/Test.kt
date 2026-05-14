package quoi.module.impl.misc

import kotlinx.coroutines.launch
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.scope
import quoi.api.abobaui.dsl.radius
import quoi.api.abobaui.dsl.size
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.commands.internal.BaseCommand
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.skyblock.Location
import quoi.api.skyblock.dungeon.Dungeon
import quoi.module.Module
import quoi.module.impl.render.ClickGui
import quoi.utils.*
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.Scheduler.wait
import quoi.utils.StringUtils.formatTime
import quoi.utils.StringUtils.toFixed
import quoi.utils.WorldUtils.nearbyBlocks
import quoi.utils.WorldUtils.state
import quoi.api.pathfinding.impl.Pathfinder
import quoi.api.pathfinding.impl.TransmissionPathfinder
import quoi.module.impl.dungeon.DungeonESP.starredMobs
import quoi.module.impl.dungeon.autoclear.MobCluster
import quoi.module.impl.dungeon.autoclear.MobClusterer
import quoi.module.impl.dungeon.autoclear.executor.ClearExecutor
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawLine
import quoi.utils.skyblock.item.TeleportUtils
import quoi.utils.skyblock.item.TeleportUtils.getEtherPos
import quoi.utils.skyblock.player.interact.AuraAction
import quoi.utils.skyblock.player.interact.AuraManager
import quoi.utils.skyblock.player.ContainerUtils
import quoi.utils.skyblock.player.LeapManager
import quoi.utils.skyblock.player.MovementUtils.moveTo
import quoi.utils.skyblock.player.PlayerUtils.eyePosition
import quoi.utils.skyblock.player.PlayerUtils.getEyeHeight
import quoi.utils.skyblock.player.RotationUtils.pitch
import quoi.utils.skyblock.player.RotationUtils.yaw
import quoi.utils.ui.hud.impl.TextHud
import quoi.utils.ui.textPair

object Test : Module("Test", desc = "Dev module for testing.") {

//    val boolBigP by switch("Bool big P")
//
//    val boolGranny by switch("Bool granny").visibleIf { boolBigP }
//    val bool1 by switch("Bool papa").childOf(::boolGranny)
//    val boolMama by switch("Bool mama").childOf(::boolGranny)
//    val bool2 by switch("Bool kid").childOf(::boolMama)
//
//    val style by selector("Style", "Box", listOf("Box", "Filled box"))
//    val outline by switch("Outline").childOf(::style) { it.index == 1 }
//    val colour by colourPicker("Colour2", Colour.WHITE, true).childOf(::style) { it.index == 0 || (outline && it.index != 0) }
//    val fillColour by colourPicker("Fill colour", Colour.WHITE, true).childOf(::style) { it.index == 1 }

    private val texttest by text("&cTest")

    private val censorsInput by textInput("Censors test", "test", placeholder = "amongus").censors()

    private val segmented by segmented("Segmented", "1", listOf("1", "2", "3"))
    private val segmented2 by segmented("Segmented enum", TextHud.HudFont.Minecraft)

    val auraDebug by switch("Aura debug")
    val uiDebug by switch("UI debug").onValueChanged { old, new -> ClickGui.reopen() }
    val reopen by button("Reopen") { ClickGui.reopen() }


//    val selectedTheme2 by SelectorSetting("Theme2", "Light", listOf("Light", "Dark", "Custom"))
//    val selectedTheme by SelectorSetting("Theme", "Light", listOf("Light", "Dark", "Custom"))
//
//    val selectorTest by SelectorSetting("class", DungeonClass.Archer)
//
//    val stringTest by StringSetting("String setting", "shit").suggests(legacyBlockIdMap.keys.map { it.replace("minecraft:", "") })

    private val showX by switch("Show X", true)
    private val showY by switch("Show Y", true)
    private val showZ by switch("Show Z", true)

    private val coords = setOf(
        Data("x:", { (mc.player?.x ?: 0.0).toFixed() }, { showX }),
        Data("y:", { (mc.player?.y ?: 0.0).toFixed() }, { showY }),
        Data("z:", { (mc.player?.z ?: 0.0).toFixed() }, { showZ })
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
        Data("P3 Section", { "${Dungeon.p3Section.name} || ${mc.player?.let { Dungeon.getP3Section().name} }" }, { p3Section }),
        Data("   Duration", { "${formatTime(Dungeon.p3Section.getDuration())} | ${formatTime(Dungeon.p3Section.getDurationTicks() * 50)}" }, { p3Section } ),
        Data("   Terminals", { "${Dungeon.p3Section.terminals}/${Dungeon.p3Section.reqTerminals}" }, { p3Section }),
        Data("   Levers", { "${Dungeon.p3Section.levers}/2" }, { p3Section }),
        Data("   Device", { "${Dungeon.p3Section.device}" }, { p3Section }),
        Data("   Gate", { Dungeon.p3Section.gate }, { p3Section }),
        Data("Container", { "${mc.screen != null} | ${ContainerUtils.containerId}" }, { container })
    )

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

    private data class Data(val name: String, val value: () -> Any?, val enabled: () -> Boolean)

    private var ticker: Ticker? = null

    fun tickerExample() = ticker {
        action { mc.player?.jumpFromGround() } // jump
        await { mc.player?.onGround() == true } // wait till player is on ground
        action(20) { modMessage("This message sent exactly 20 ticks after landing.") } // send message 20 ticks later
    }

    init {
        val command = BaseCommand("quoitest")
//        command.sub("scheduletest") {
//            var start = 0
//            scheduleLoop(20) {
//                start += 20
//                modMessage("TEST $start")
//                if (start >= 100) it.cancel()
//            }
//        }

//        command.sub("tickertest") {
//            ticker = tickerExample() // set ticker
//        }

        command.sub("moveto") {
//            val path = listOf(
//                BlockPos(92, 70, -225),
//                BlockPos(91, 71, -220),
//                BlockPos(97, 71, -219),
//                BlockPos(102, 72, -216),
//                BlockPos(104, 73, -213),
//            )

            val path = path ?: return@sub

            player.moveTo(path)
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
            mc.hitResult?.let {
                if (it !is BlockHitResult) return@let
                AuraManager.breakBlock(it.blockPos, custom = custom ?: false)
            }
        }

        command.sub("raycast") {
            val res = rayCast()
            modMessage(res)
        }

        command.sub("testshit") {
            stupid = 0
            scope.launch {
                modMessage("$stupid: shit")
                wait(5)
                modMessage("$stupid: shit2")
                wait(20)
                modMessage("$stupid: shit3")
            }
        }

        command.sub("testleap") { player: String ->
            scheduleTask(20) {
                LeapManager.leap(player)
            }
        }.suggests { WorldUtils.players.map { it.profile.name } }

        val upd = command.sub("testcommand")
        upd.sub("coords") { x: Int, y: Int, z: Int ->
            modMessage("coords: $x, $y, $z")
        }
        .suggests("x") { player.x.toInt() }
        .suggests("y") { player.y.toInt() }
        .suggests("z") { player.z.toInt() }

        upd.sub("health") { hp: Int? ->
            modMessage("health: $hp")
        }.suggests(26)

        on<TickEvent.End> {
            stupid++
            ticker?.let { // do ticker
                if (it.tick()) ticker = null
            }
        }

        command.sub("block") {
            val shit = player.eyePosition().nearbyBlocks(30f) { it.state.block == Blocks.DIAMOND_ORE }
            modMessage(shit)
        }

        command.sub("path") {
            val start = player.blockPosition()
            val goal = BlockPos(-121, 68, -153)

            scope.launch {
                val p = Pathfinder.findPath(start, goal, maxNodes = 40_000) ?: return@launch
                path = p

                val path = p.map { it.center.addVec(y = 0.5) }
                val points = mutableListOf<Vec3>()

                for (i in path.indices) {
                    val node = path[i]

                    if (i > 0 && i < path.size -1) {
                        val start = node.add(path[i - 1].subtract(node).scale(0.5))
                        val end = node.add(path[i + 1].subtract(node).scale(0.5))

                        for (j in 0..5) {
                            val t = j / 5.0

                            val x = t.lerp(t.lerp(start.x, node.x), t.lerp(node.x, end.x))
                            val z = t.lerp(t.lerp(start.z, node.z), t.lerp(node.z, end.z))

                            points.add(Vec3(x, node.y, z))
                        }
                    } else points.add(node)
                }

                drawPoints = points
            }
        }

        command.sub("f0click") {
            scope.launch {
                ContainerUtils.getContainerItemsClick(
                    command = "joininstance catacombs_entrance",
                    container = "Undersized party!",
                    name = "Undersized party!",
                    slots = 36,
                )
            }
        }

        command.sub("etherpath") {
            val start = player.blockPosition().below()
            val goal = BlockPos(-35, 79, -73)

//            scope.launch {
//                val p = EtherwarpPathfinder.findPath(
//                    start,
//                    goal,
//                    pitchStep = 22f,
//                    yawStep = 22f,
//                    hWeight = 5.0,
//                    threads = 4
//                ) ?: return@launch
////                etherPath = p
////                etherPoints = etherPath!!.map { Vec3(it.x + 0.5, it.y + 1.0, it.z + 0.5) }
//            }
//            val y = 23.094011f
//            val p = 30f
//            modMessage(player.eyePosition(true).addVec(y = 0.05).getEtherPos(y, p))
            modMessage(TeleportUtils.predictTransmission(player.position(), player.yaw, player.pitch))
        }

        command.sub("transpath") {
//            val room = Dungeon.currentRoom ?: return@sub modMessage("room is null")
//            val start = player.position()
            val goal = BlockPos(-26, 69, -136)
//
//            modMessage(room.starredMobs)
//
//            scope.launch {
//                val p = TransmissionPathfinder.findClearPath(
//                    start = start,
//                    mobs = room.starredMobs,
//                    pitchStep = 10f,
//                    yawStep = 10f,
//                    hWeight = 5.0,
//                    threads = 16
//                ) ?: return@launch modMessage("no trans path")
//
//                etherPath = p.map { it.pos }
//
//                var curr = start.addVec(y = getEyeHeight(false))
//
//                etherPoints = p.map { node ->
//                    val look = getLook(node.yaw, node.pitch)
//                    val target = curr.add(look.scale(10.0))
//                    val seg = curr to target
//                    curr = node.pos.center.addVec(y = -0.5 + getEyeHeight(false))
//
//                    seg
//                }.drop(1)
//            }
            ClearExecutor.testPath(to = goal)
        }

        command.sub("spawnstarred") {
            ChatUtils.command("/summon zombie ~ ~ ~ {NoAI:1b, PersistenceRequired:1b, Passengers:[{id:\"minecraft:armor_stand\", Marker:1b, CustomNameVisible:1b, CustomName:'✯ \\u00A7c❤', NoGravity:1b}]}")
        }

        command.sub("findclusters") {
            val room = Dungeon.currentRoom ?: return@sub modMessage("room i snull")
            clusters = MobClusterer.getOrderedClusters(player.position(), room.starredMobs)
        }

        on<RenderEvent.World> {
            if (drawPoints != null && path != null) {
                val points = drawPoints!!
                val path = path!!

                path.forEach { pos ->
                    ctx.drawFilledBox(pos.aabb, colour = Colour.ORANGE, depth = true)
                }
                ctx.drawLine(points, colour = Colour.WHITE, depth = true)
            }

            if (etherPoints != null && etherPath != null) {
                val points = etherPoints!!
                val path = etherPath!!
                val eye = getEyeHeight(false).toDouble()
                path.forEach { pos ->
                    ctx.drawFilledBox(pos.aabb.deflate(0.4, 0.0, 0.4).setMaxY(pos.y + eye), colour = Colour.ORANGE.withAlpha(100), depth = true)
                    ctx.drawFilledBox(pos.aabb.setMaxY(pos.y + 0.1), colour = Colour.GREEN.withAlpha(100), depth = true)
                }
//                ctx.drawLine(points, colour = Colour.WHITE, depth = true)
                points.forEach { (vec3, vec31) ->
                    ctx.drawLine(listOf(vec3, vec31), colour = Colour.WHITE, depth = true)
                }
            }

            if (clusters != null) {
                val clusters = clusters!!

                clusters.forEach { (seed, mobs) ->
                    ctx.drawFilledBox(seed.aabb, colour = Colour.WHITE, depth = true)
                    mobs.forEach {
                        ctx.drawFilledBox(it.position().aabb(0.5), colour = Colour.PINK.withAlpha(100), depth = true)
                    }
                }
            }
        }

        command.register()
    }
    private var stupid = 0

    private var path: List<BlockPos>? = null
    private var drawPoints: List<Vec3>? = null

    private var etherPath: List<BlockPos>? = null
    private var etherPoints: List<Pair<Vec3, Vec3>>? = null

    private var clusters: List<MobCluster>? = null
}