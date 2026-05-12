package quoi.module.impl.dungeon

import kotlinx.coroutines.launch
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.monster.Giant
import net.minecraft.world.item.Items
import quoi.QuoiMod
import quoi.api.events.ChatEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.pathfinding.impl.Pathfinder
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Dungeon.BLOOD_START_REGEX
import quoi.api.skyblock.dungeon.Dungeon.currentRoom
import quoi.api.skyblock.dungeon.Floor
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomType
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.ChatUtils
import quoi.utils.ChatUtils.modMessage
import quoi.utils.EntityUtils.getEntities
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.StringUtils.width
import quoi.utils.Ticker
import quoi.utils.WorldUtils.nearbyBlocks
import quoi.utils.WorldUtils.walkable
import quoi.utils.addVec
import quoi.utils.distanceTo
import quoi.utils.distanceToSqr
import quoi.utils.equalsOneOf
import quoi.utils.getDirection
import quoi.utils.isPathClear
import quoi.utils.render.DrawContextUtils.drawText
import quoi.utils.scaledHeight
import quoi.utils.scaledWidth
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.ContainerUtils
import quoi.utils.skyblock.player.MovementUtils.cancelMovementTask
import quoi.utils.skyblock.player.MovementUtils.moveTo
import quoi.utils.skyblock.player.MovementUtils.moving
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.PlayerUtils.leftClick
import quoi.utils.skyblock.player.PlayerUtils.useItem
import quoi.utils.skyblock.player.RotationUtils.rotate
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.ticker

object AutoFragRun : Module(
    "Auto Frag Run",
) {
    private val info by text("""
        You &cmust&r have AutoBloodRush module enabled.
        Bow uses &njewjew&r or &nterminator&r, use archer class for better efficiency
        Melee uses dungeon &nswords&r or &nlongswords&r, use berserker class
    """.trimIndent())

    private val weapon by segmented("Weapon", Weapon.Hype)
    private val meleeRange by rangeSlider("Melee range", 2.5 to 3.5, 2.5, 6.0, 0.5).visibleIf { weapon.selected == Weapon.Melee }

    private var giant: BloodGiant? = null
    private var gettingGiant = false

    private val fragRunEnv get() = Dungeon.floor == Floor.F7 && Dungeon.dungeonTeammatesNoSelf.isEmpty()
    private val waitSpot = BlockPos(1, 68, 20)

    private var status = FragStatus.IDLE
    private var tickerThing: Ticker? = null
    private var requeue = false
    private var worldLoaded = false

    init {
        on<ChatEvent.Packet> {
            val noCodes = message.noControlCodes
            if (noCodes.contains("Team Score: 0 (D)")) requeue("Died", true)
            if (noCodes.contains("You cannot join this instance!")) requeue("Can't join instance, retrying")
            if (!BLOOD_START_REGEX.matches(noCodes)) return@on
            if (!fragRunEnv || requeue || currentRoom?.data?.type != RoomType.BLOOD) return@on
            status = FragStatus.IDLE_BLOOD
            scheduleTask(60, server = true) {
                if (player.y != 69.0) return@scheduleTask requeue("Stuck after br", true)
                val goal = currentRoom!!.getRealCoords(waitSpot)

                val path = Pathfinder.findPath(player.blockPosition(), goal) ?: return@scheduleTask requeue("Stuck, can't find path")
                status = FragStatus.SPOT
                player.moveTo(path) {
                    status = FragStatus.GIANT_WAIT
                }
            }
        }

        on<RenderEvent.Overlay> {
            val t = status.desc
            val x = (scaledWidth - t.width()) / 2f
            val y = (scaledHeight + 40) / 2f
            ctx.drawText(t, x, y)
        }

        on<TickEvent.End> {
            tickerThing?.tick()

            if (!fragRunEnv) return@on

            if (Dungeon.warpCooldown == 0L && status == FragStatus.IDLE && !requeue) {
                requeue("Warp cd is over, not in blood")
                return@on
            }

            if (Dungeon.isDead && !requeue) {
                requeue("Died", true)
                return@on
            }

            if (currentRoom?.data?.type != RoomType.BLOOD) return@on

            giant?.let { g ->
                if (!g.entity.isAlive) {
                    giant = null
                    weapon.selected.kill(g)
                }
                weapon.selected.tick(g)
                return@on
            }

            if (gettingGiant) return@on
            val entity = getEntities<Giant>(radius = 20.0).firstOrNull() ?: return@on
            gettingGiant = true
            scheduleTask(10, server = true) {
                if (giant != null) return@scheduleTask
                val headItem = entity.getItemBySlot(EquipmentSlot.HEAD)
                val type = when (headItem.item) {
                    Items.AIR, Items.TNT -> GiantType.BIGFOOT
                    Items.DIAMOND_HELMET -> GiantType.DIAMOND
                    Items.LEATHER_HELMET -> if (headItem.get(DataComponents.DYED_COLOR)?.rgb == 16716947) GiantType.PINK else GiantType.LASER
                    else -> GiantType.UNKNOWN
                }

                if (!type.equalsOneOf(GiantType.LASER, GiantType.DIAMOND)) {
                    requeue("Shit giant ($type)")
                    return@scheduleTask
                }

                giant = BloodGiant(entity, type)
//                modMessage("found giant $giant")
                weapon.selected.spawn(giant!!)
            }
        }

        on<WorldEvent.Load.Start> {
            worldLoaded = true
        }

        on<WorldEvent.Change> {
            giant = null
            gettingGiant = false

            requeue = false
            status = FragStatus.IDLE
//            tickerThing = null
        }
    }

    private fun requeue(reason: String, withDh: Boolean = false) {
        if (requeue) return
        requeue = true
        modMessage("Requeue: $reason")
        cancelMovementTask()

        tickerThing = ticker {
            if (withDh) {
                action {
                    status = FragStatus.WARP_DH
                    worldLoaded = false
                    ChatUtils.command("warp dh")
                }

                await { worldLoaded }
                delay(5)
            }

            await {
                status = FragStatus.WARP_CD
                Dungeon.warpCooldown == 0L
            }

            action {
                status = FragStatus.REQUEUE
                QuoiMod.scope.launch {
                    ContainerUtils.getContainerItemsClick(
                        command = "joininstance catacombs_floor_seven",
                        container = "Undersized party!",
                        name = "Undersized party!",
                        slots = 36,
                    )
                }
            }
        }
    }

    private fun move(pos: BlockPos) {
        val path = Pathfinder.findPath(player.blockPosition(), pos)
            ?: return requeue("Stuck, no path to giant")
        status = FragStatus.GIANT_SPOT
        player.moveTo(path) {
            status = FragStatus.KILLING
        }
    }

    private data class BloodGiant(val entity: Giant, val type: GiantType) {
        val pos: BlockPos get() = entity.blockPosition().atY(68)
    }
    private enum class GiantType {
        LASER, DIAMOND, BIGFOOT, PINK, UNKNOWN;
    }
    private enum class FragStatus(val desc: String) {
        IDLE("Idle"),
        IDLE_BLOOD("Idle in blood"),
        SPOT("Going to waiting spot"),
        GIANT_WAIT("Waiting for giant spawn"),
        GIANT_SPOT("Going to giant"),
        KILLING("Killing giant"),
        PICKUP("Picking up the drop"),
        WARP_CD("Waiting for warp cooldown"),
        WARP_DH("Warping to dungeon hub"),
        REQUEUE("Requeueieng")
    }

    private enum class Weapon {
        Hype {
            override fun spawn(giant: BloodGiant) {
                SwapManager.swapById("HYPERION")
                move(giant.pos)
            }

            override fun tick(giant: BloodGiant) {
                if (player.mainHandItem.skyblockId != "HYPERION") return
                if (player.tickCount % 2 == 0) return
                if (moving()) return

                val dir = getDirection(giant.pos.center)
                player.useItem(dir)
            }

            override fun kill(giant: BloodGiant) {
                requeue("Killed")
            }
        },

        Bow {
            private var spot: BlockPos? = null

            override fun spawn(giant: BloodGiant) {
                SwapManager.swapById("TERMINATOR", "JUJU_SHORTBOW")
                status = FragStatus.KILLING
            }

            override fun tick(giant: BloodGiant) {
                if (status == FragStatus.KILLING) {
                    if (spot == null || player.blockPosition().distanceToSqr(spot!!) <= 2.25 || !moving()) {
                        val new = getSpot(giant)
                        if (new != null) {
                            spot = new
                            val path = Pathfinder.findPath(player.blockPosition(), new) ?: return
                            player.moveTo(path)
                        }
                    }
                }

                if (player.tickCount % 4 == 0) return
                val dir = getDirection(giant.pos.center.addVec(y = 5))
//                player.useItem(dir)
                player.rotate(dir)
                PlayerUtils.interact()
            }

            private fun getSpot(giant: BloodGiant): BlockPos? {
                val room = currentRoom ?: return null
                val centre = room.getRealCoords(BlockPos(15, 68, 15))
                val candidates = centre.nearbyBlocks(8f) { pos ->
                    pos.y == 69 &&
                    pos.walkable &&
                    pos.distanceTo(player.blockPosition()) > 5.0 &&
                    pos.distanceTo(giant.pos) > 5.0 &&
                    isPathClear(pos.center.addVec(y = 1.5), giant.pos.center.addVec(y = 5.0))
                }

                return candidates.randomOrNull()?.atY(68)
            }
        },

        Melee {
            private var spot: BlockPos? = null

            override fun spawn(giant: BloodGiant) {
                if (!SwapManager.swapByLore("DUNGEON SWORD").success) {
                    SwapManager.swapByLore("DUNGEON LONGSWORD")
                }
                move(giant.pos)
            }

            override fun tick(giant: BloodGiant) {
                if (status == FragStatus.KILLING) {
                    if (spot == null || player.blockPosition().distanceToSqr(spot!!) <= 1.0 || !moving()) {
                        val new = getSpot(giant)
                        if (new != null) {
                            spot = new
                            val path = Pathfinder.findPath(player.blockPosition(), new) ?: return
                            player.moveTo(path)
                        }
                    }

                    if (player.tickCount % 2 == 0) return
                    val dir = getDirection(giant.pos.center.addVec(y = 2))
                    player.rotate(dir)
                    player.leftClick()
                }
            }

            fun getSpot(giant: BloodGiant): BlockPos? {
                val candidates = giant.pos.nearbyBlocks((meleeRange.second + 1).toFloat()) { pos ->
                    pos.y == 69 &&
                    pos.walkable &&
                    pos.distanceTo(giant.pos) in meleeRange.first..meleeRange.second &&
                    pos.distanceTo(player.blockPosition()) > 2.0
                }

                return candidates.randomOrNull()?.atY(68)
            }
        };

        abstract fun spawn(giant: BloodGiant)
        abstract fun tick(giant: BloodGiant)
        open fun kill(giant: BloodGiant) {
            val path = Pathfinder.findPath(player.blockPosition(), giant.pos) ?: return requeue("Killed")
            status = FragStatus.PICKUP
            player.moveTo(path) {
                scheduleTask(5) { requeue("Killed") }
            }
        }
    }
}