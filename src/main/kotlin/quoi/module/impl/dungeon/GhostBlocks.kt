package quoi.module.impl.dungeon

import quoi.api.commands.internal.BaseCommand
import quoi.api.events.PacketEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.skyblock.dungeon.Dungeon.inBoss
import quoi.api.skyblock.dungeon.Dungeon.inDungeons
import quoi.config.configList
import quoi.module.Module
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.BooleanSetting
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.width
import quoi.utils.render.DrawContextUtils.drawString
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.level.block.Blocks

// Kyleen
object GhostBlocks : Module( // this shit seems so fucking useless.
    "Ghost Blocks",
    desc = "Set useless levers in lights device to air. /gb help"
) {

    private val dungeonsOnly by BooleanSetting("Dungeons only")
    private val bossOnly by BooleanSetting("Boss only").withDependency { dungeonsOnly }

    private val ghostBlocks by configList<BlockPos>("ghostblocks.json")
    private var editMode = false
    private val ghostBlockCommand = BaseCommand("ghostblock", "gb").requires("&cGhost blocks module is disabled!") { enabled }

    private val ghostBlocksPreset = listOf(
        BlockPos(61, 136, 142),
        BlockPos(60, 136, 142),
        BlockPos(59, 136, 142),
        BlockPos(58, 135, 142),
        BlockPos(59, 135, 142),
        BlockPos(61, 135, 142),
        BlockPos(62, 135, 142),
        BlockPos(62, 134, 142),
        BlockPos(61, 134, 142),
        BlockPos(59, 134, 142),
        BlockPos(58, 134, 142),
        BlockPos(59, 133, 142),
        BlockPos(60, 133, 142),
        BlockPos(61, 133, 142)
    )

    private val defaultGhostBlocksPreset = ghostBlocksPreset.toList()

    init {
        ghostBlockCommand.sub("edit") {
            editMode = !editMode
            modMessage(if (editMode) "&7Ghostblocks edit mode &aenabled." else "&7Ghostblocks edit mode &cdisabled.", id = 101101)
        }.description("Toggle Ghostblocks edit mode.")

        ghostBlockCommand.sub("clear") {
            ghostBlocks.clear()
            modMessage("&cCleared all ghost blocks.")
        }.description("Remove all ghost blocks.")

        ghostBlockCommand.sub("undo") {
            if (ghostBlocks.isEmpty()) {
                modMessage("&cNo blocks to undo.")
                return@sub
            }
            val removed = ghostBlocks.removeLast()
            modMessage("&eRemoved: &7${removed.x}, ${removed.y}, ${removed.z}")
        }.description("Remove the last added block.")

        ghostBlockCommand.sub("preset") {
            ghostBlocks.clear()
            ghostBlocks.addAll(defaultGhostBlocksPreset)
            modMessage("&aLoaded default ghost blocks preset.")
        }.description("Load the default ghost blocks.")

        ghostBlockCommand.register()

        on<TickEvent.End> {
            if (dungeonsOnly && !inDungeons) return@on
            if (bossOnly && !inBoss) return@on

            ghostBlocks.forEach { pos ->
                val state = level.getBlockState(pos)
                if (!state.isAir) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 0)
                }
            }
        }

        on<PacketEvent.Sent, ServerboundUseItemOnPacket> {
            if (!editMode) return@on

            val pos = packet.hitResult.blockPos

            ghostBlocks.add(pos)
            modMessage("&aAdded ghost block: &7${pos.x}, ${pos.y}, ${pos.z}")
        }

        on<RenderEvent.Overlay> {
            if (!editMode) return@on
            val x = (mc.window.guiScaledWidth - "GB Edit Mode".width()) / 2f
            val y = (mc.window.guiScaledHeight + 40) / 2f
            ctx.drawString("GB Edit Mode", x, y)
        }
    }
}