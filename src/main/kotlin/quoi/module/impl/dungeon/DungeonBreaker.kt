package quoi.module.impl.dungeon

import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.commands.internal.BaseCommand
import quoi.api.events.ChatEvent
import quoi.api.events.PacketEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon.inBoss
import quoi.api.skyblock.dungeon.Dungeon.inDungeons
import quoi.api.skyblock.dungeon.Dungeon.isProtectedBlock
import quoi.config.configList
import quoi.module.Module
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.BooleanSetting
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.StringUtils.width
import quoi.utils.render.DrawContextUtils.drawString
import quoi.utils.render.drawFilledBox
import quoi.utils.skyblock.ItemUtils.lore
import quoi.utils.skyblock.ItemUtils.skyblockId
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.skyblock.player.SwapResult
import quoi.utils.ui.hud.TextHud
import quoi.utils.ui.hud.setting
import quoi.utils.ui.textPair
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.ItemStack
import java.util.concurrent.ConcurrentHashMap

// Kyleen
object DungeonBreaker : Module(
    "Dungeon Breaker",
    area = Island.Dungeon
) {

    private val zeroPingDungeonBreaker by BooleanSetting("Zero ping", desc = "Insta-mine blocks.")
    private val onlyWhenFatigue by BooleanSetting("Only insta-mine with fatigue", desc = "Only insta-mine blocks when mining fatigue is applied.").withDependency { zeroPingDungeonBreaker }
    private val autoDungeonBreaker by BooleanSetting("Auto dungeon breaker", desc = "Automatically mines preset route when in boss. /db help")
    private val breakerBlocks by configList<BlockPos>("dungeonbreaker_blocks.json")

    private val activeBreakerBlocks = ConcurrentHashMap.newKeySet<BlockPos>()
    private var forceRender = false
    private val chargesRegex = Regex("Charges: (\\d+)/(\\d+)⸕")

    private val dungeonBreakerPreset = listOf(
        BlockPos(73, 220, 32), BlockPos(73, 220, 33), BlockPos(73, 220, 34), BlockPos(73, 220, 35), BlockPos(73, 220, 36),
        BlockPos(19, 167, 72), BlockPos(19, 168, 72), BlockPos(18, 168, 72), BlockPos(18, 167, 72), BlockPos(17, 168, 72),
        BlockPos(17, 167, 72), BlockPos(16, 168, 72), BlockPos(16, 167, 72), BlockPos(15, 168, 72), BlockPos(15, 167, 72),
        BlockPos(14, 168, 72), BlockPos(14, 167, 72), BlockPos(13, 168, 72), BlockPos(13, 167, 72), BlockPos(12, 168, 72),
        BlockPos(12, 167, 72), BlockPos(12, 166, 72), BlockPos(12, 165, 72), BlockPos(12, 164, 72), BlockPos(12, 163, 72),
        BlockPos(12, 162, 72), BlockPos(12, 161, 72), BlockPos(12, 160, 72), BlockPos(12, 159, 72), BlockPos(12, 158, 72),
        BlockPos(12, 157, 72), BlockPos(12, 156, 72), BlockPos(12, 155, 72), BlockPos(12, 154, 72), BlockPos(12, 153, 72),
        BlockPos(12, 152, 72), BlockPos(12, 151, 72), BlockPos(12, 150, 72), BlockPos(12, 149, 72), BlockPos(12, 148, 72),
        BlockPos(12, 147, 72), BlockPos(16, 117, 127), BlockPos(16, 116, 127), BlockPos(16, 115, 127), BlockPos(17, 117, 127),
        BlockPos(17, 116, 127), BlockPos(17, 115, 127), BlockPos(18, 117, 127), BlockPos(18, 116, 127), BlockPos(18, 115, 127),
        BlockPos(95, 117, 124), BlockPos(95, 116, 124), BlockPos(95, 115, 124), BlockPos(95, 117, 123), BlockPos(95, 116, 123),
        BlockPos(95, 115, 123), BlockPos(95, 117, 122), BlockPos(95, 116, 122), BlockPos(95, 115, 122), BlockPos(57, 113, 110),
        BlockPos(57, 113, 111), BlockPos(57, 113, 112), BlockPos(54, 116, 54), BlockPos(54, 115, 54), BlockPos(53, 63, 115),
        BlockPos(54, 63, 115), BlockPos(55, 63, 115), BlockPos(55, 63, 114), BlockPos(54, 63, 114), BlockPos(53, 63, 114),
        BlockPos(53, 63, 113), BlockPos(54, 63, 113), BlockPos(55, 63, 113)
    )

    private val defaultBreakerPreset = dungeonBreakerPreset.toList()
    private var editMode = false
    private var lastClickedBlock: BlockPos? = null

    private val dungeonBreakerCommand = BaseCommand("dungeonbreaker", "db").requires("&cDungeon Breaker module is disabled!") { enabled }

    private val chargesHud by TextHud("Charges display") {
        visibleIf { mc.player != null && inDungeons && getBreakerCharges(player.mainHandItem) > 0 }
        textPair(
            string = "Charges:",
            supplier = { mc.player?.let { getBreakerCharges(it.mainHandItem) } ?: 0 },
            labelColour = colour,
            shadow = shadow
        )
    }.setting()

    init {
        dungeonBreakerCommand.sub("em") {
            editMode = !editMode
            modMessage(if (editMode) "&7DB edit mode &aenabled." else "&7DB edit mode &cdisabled.", id = 1664)
        }.description("Toggle dungeon breaker edit mode.")

        dungeonBreakerCommand.sub("clear") {
            breakerBlocks.clear()
            modMessage("&cCleared all dungeon breaker blocks.")
        }.description("Remove all saved breaker blocks.")

        dungeonBreakerCommand.sub("debug") {
            forceRender = !forceRender
            modMessage(if (forceRender) "&7DB forceRender &aenabled." else "&7DB forceRender &cdisabled.", id = 999)
        }.description("Debug")

        dungeonBreakerCommand.sub("preset") {
            breakerBlocks.clear()
            breakerBlocks.addAll(defaultBreakerPreset)
            modMessage("&aReset dungeon breaker blocks to default preset.")
        }.description("Load the default breaker route.")

        dungeonBreakerCommand.sub("undo") {
            if (breakerBlocks.isEmpty()) {
                modMessage("&cNo blocks to undo.")
                return@sub
            }

            val removed = breakerBlocks.removeLast()
            modMessage("&eRemoved block: &7${removed.x}, ${removed.y}, ${removed.z}")
        }.description("Remove the last added block.")

        dungeonBreakerCommand.register()

        on<WorldEvent.Change> {
            activeBreakerBlocks.clear()
        }

        on<RenderEvent.World> {
            if (autoDungeonBreaker && activeBreakerBlocks.isNotEmpty() && (inBoss || forceRender)) {
                activeBreakerBlocks.forEach { pos ->
                    ctx.drawFilledBox(AABB(pos), Colour.GREEN.withAlpha(60))
                }
            }

            if (editMode) {
                breakerBlocks.forEach {
                    ctx.drawFilledBox(AABB(it), Colour.RED.withAlpha(60))
                }
            }
        }

        on<PacketEvent.Sent, ServerboundPlayerActionPacket> {
            if (!zeroPingDungeonBreaker) return@on
            if (editMode) return@on
            if (onlyWhenFatigue && !player.hasEffect(MobEffects.MINING_FATIGUE)) return@on
            if (packet.action != ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) return@on

            val packetPos = packet.pos

            mc.execute {
                val heldItem = player.mainHandItem
                if (getBreakerCharges(heldItem) <= 0) return@execute

                if (isProtectedBlock(packetPos)) return@execute

                val clipResult = level.clip(
                    ClipContext(
                        player.eyePosition,
                        Vec3.atCenterOf(packetPos),
                        ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE,
                        player
                    )
                )

                if (clipResult.type == HitResult.Type.BLOCK && clipResult.blockPos == packetPos) {
                    level.setBlock(packetPos, Blocks.AIR.defaultBlockState(), 3)
                }
            }
        }

        on<PacketEvent.Sent, ServerboundUseItemOnPacket> {
            if (!editMode) return@on

            val hit = packet.hitResult
            val pos = hit.blockPos

            if (lastClickedBlock == pos) return@on
            lastClickedBlock = pos

            if (isProtectedBlock(pos)) return@on

            if (breakerBlocks.any { it == pos }) {
                breakerBlocks.remove(pos)
                modMessage("&cRemoved block: &7${pos.x}, ${pos.y}, ${pos.z}")
            } else {
                breakerBlocks.add(pos)
                modMessage("&aAdded block: &7${pos.x}, ${pos.y}, ${pos.z}")
            }
        }

        on<TickEvent.Start> {
            lastClickedBlock = null
            if (!autoDungeonBreaker || !inBoss || activeBreakerBlocks.isEmpty()) return@on

            val playerPos = player.position()

            val targetBlock = activeBreakerBlocks.minByOrNull {
                it.distToCenterSqr(playerPos.x, playerPos.y, playerPos.z)
            } ?: return@on

            if (!level.isLoaded(targetBlock)) return@on

            if (level.getBlockState(targetBlock).isAir) {
                activeBreakerBlocks.remove(targetBlock)
                return@on
            }

            if (targetBlock.distToCenterSqr(playerPos.x, playerPos.y, playerPos.z) > 25.0) return@on

            val breakerSlot = (0..8).find { slot ->
                getBreakerCharges(player.inventory.getItem(slot)) > 0
            }

            if (breakerSlot == null) return@on

            val swapResult = SwapManager.swapToSlot(breakerSlot)

            if (swapResult == SwapResult.SUCCESS) return@on

            if (swapResult == SwapResult.ALREADY_SELECTED) {
                val center = Vec3.atCenterOf(targetBlock)

                val clipResult = level.clip(
                    ClipContext(
                        player.eyePosition,
                        center,
                        ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE,
                        player
                    )
                )

                if (clipResult.type == HitResult.Type.BLOCK && clipResult.blockPos == targetBlock) {
                    mc.connection?.send(
                        ServerboundPlayerActionPacket(
                            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                            targetBlock,
                            clipResult.direction
                        )
                    )
                    player.swing(InteractionHand.MAIN_HAND)
                }
            }
        }

        on<ChatEvent.Packet> {
            when (message.noControlCodes) {
                "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!" -> {
                    activeBreakerBlocks.clear()
                    activeBreakerBlocks.addAll(
                        if (breakerBlocks.isEmpty()) defaultBreakerPreset else breakerBlocks
                    )
                }
            }
        }

        on<RenderEvent.Overlay> {
            if (!editMode) return@on
            val x = (mc.window.guiScaledWidth - "DB Edit Mode".width()) / 2f
            val y = (mc.window.guiScaledHeight + 40) / 2f
            ctx.drawString("DB Edit Mode", x, y)
        }

    }

    private fun getBreakerCharges(stack: ItemStack): Int {
        if (stack.isEmpty || stack.skyblockId != "DUNGEONBREAKER") return 0

        val lore = stack.lore ?: return 0
        val loreStringList = lore.asSequence().map { it.noControlCodes }

        val charges = loreStringList.firstNotNullOfOrNull { line ->
            chargesRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()
        } ?: 0

        return charges
    }
}