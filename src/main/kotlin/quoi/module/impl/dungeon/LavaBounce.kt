package quoi.module.impl.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.commands.internal.BaseCommand
import quoi.api.events.MouseEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.config.ConfigList
import quoi.config.ConfigSystem
import quoi.config.configPath
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.ChatUtils.modMessage
import quoi.utils.WorldUtils.state
import quoi.utils.equalsOneOf
import quoi.utils.getEyeHeight
import quoi.utils.render.drawFilledBox
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.skyblock.player.interact.AuraManager
import java.io.File

object LavaBounce : Module(
    "Lava Bounce",
    area = Island.Dungeon
) {
    private val cooldown by slider("Cooldown", 500, 0, 2000, 50)
    private val addRange by slider("Add Range", 4.5, 1.0, 50.0, 0.1)
    private val useConfig by switch("Use Config", true)
    private val renderBlocks by switch("Render Blocks", true).visibleIf { useConfig }
    private val colour by colourPicker("Colour", Colour.RED.withAlpha(0.35f), allowAlpha = true).visibleIf { useConfig }
    
    private val configFile = File(configPath, "lavabounce.json")
    private val loadedBlocks = ConfigSystem.load(configFile) { mutableListOf<BlockPos>() }
    private val blocks = ConfigList(loadedBlocks, {
        ConfigSystem.save(configFile, loadedBlocks)
    }, {
        val new = ConfigSystem.load(configFile) { mutableListOf<BlockPos>() }
        loadedBlocks.clear()
        loadedBlocks.addAll(new)
    })
    
    private val cooldownMap = mutableMapOf<Int, Long>()
    private var editMode = false
    
    private val cmd = BaseCommand("lavabounce", "lb")
    
    init {
        cmd.sub("em") {
            editMode = !editMode
            modMessage("Edit mode ${if (editMode) "&aenabled" else "&cdisabled"}&r!")
        }.description("Toggle edit mode")
        
        cmd.sub("edit") {
            editMode = !editMode
            modMessage("Edit mode ${if (editMode) "&aenabled" else "&cdisabled"}&r!")
        }.description("Toggle edit mode")
        
        cmd.register()
        
        on<MouseEvent.Click> {
            if (!editMode || button != 1 || !state) return@on
            
            val result = player.pick(addRange, 1f, true)
            if (result !is BlockHitResult || result.type == HitResult.Type.MISS) return@on
            
            val bp = result.blockPos
            if (!mc.level?.getBlockState(bp)?.`is`(Blocks.LAVA)!!) return@on
            
            val pos = result.blockPos
            
            if (pos in blocks) {
                blocks.remove(pos)
                modMessage("&cRemoved &e$pos")
            } else {
                blocks.add(pos)
                modMessage("&aAdded &e$pos")
            }
            blocks.save()
        }
        
        on<TickEvent.End> {
            if (!enabled) return@on
            if (player.isInLava || player.onGround()) return@on
            
            val under = findLava() ?: return@on
            
            if (useConfig && under.above() !in blocks) return@on
            
            val state = under.state
            if (state.getShape(mc.level, under).isEmpty) return@on
            
            val eyePos = player.position().add(0.0, player.getEyeHeight(player.pose).toDouble(), 0.0)
            val top = Vec3(under.x + 0.5, under.y + 0.999, under.z + 0.5)
            
            val motionY = player.deltaMovement.y
            val nextMotionY = maxOf((motionY - 0.08) * 0.98, -3.9) * 2
            val nextPos = nextMotionY + player.y
            
            if (nextPos > top.y || eyePos.distanceToSqr(top) > 20.25) return@on
            
            if (!SwapManager.swapById("SOUL_SAND", "CHEST", "ENDER_CHEST").success) return@on
            
            val now = System.currentTimeMillis()
            val hash = under.hashCode()
            if (now - cooldownMap.getOrDefault(hash, 0L) < cooldown) return@on
            
            val level = mc.level ?: return@on
            val blockState = level.getBlockState(under)
            val blockAABB = blockState.getShape(level, under).bounds()
            val result = collisionRayTrace(under, blockAABB, eyePos, top) ?: return@on
            
            startUseItem(result)
            cooldownMap[hash] = now
        }
        
        on<RenderEvent.World> {
            if (!renderBlocks || !useConfig || blocks.isEmpty()) return@on
            if (!editMode && !enabled) return@on
            
            blocks.forEach { pos ->
                val aabb = AABB(
                    pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
                    pos.x + 1.0, pos.y + 1.0, pos.z + 1.0
                )
                ctx.drawFilledBox(aabb, colour, depth = true)
            }
        }
    }
    
    private fun findLava(): BlockPos? {
        val bp = player.blockPosition().mutable()
        val y = player.blockY
        
        for (i in y downTo 1) {
            bp.y = i
            val state = mc.level?.getBlockState(bp) ?: return null
            
            when {
                state.`is`(Blocks.LAVA) -> return bp.setY(i - 1).immutable()
                !state.`is`(Blocks.AIR) -> return null
            }
        }
        return null
    }
    
    private fun collisionRayTrace(pos: BlockPos, aabb: AABB, start: Vec3, end: Vec3): BlockHitResult? {
        val localStart = start.subtract(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
        val localEnd = end.subtract(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
        
        val hit = aabb.clip(localStart, localEnd).orElse(null) ?: return null
        
        val hitPosWorld = hit.add(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
        val direction = Direction.getApproximateNearest(
            hit.x() - aabb.center.x,
            hit.y() - aabb.center.y,
            hit.z() - aabb.center.z
        )
        return BlockHitResult(hitPosWorld, direction, pos, false)
    }
    
    private fun startUseItem(hitResult: BlockHitResult) {
        for (hand in InteractionHand.entries) {
            val itemStack = player.getItemInHand(hand)
            if (!itemStack.isItemEnabled(mc.level?.enabledFeatures())) return
            
            val interactionResult = mc.gameMode?.useItemOn(player, hand, hitResult) ?: return
            if (interactionResult is InteractionResult.Success) {
                if (interactionResult.swingSource() == InteractionResult.SwingSource.CLIENT) {
                    player.swing(hand)
                    if (!itemStack.isEmpty && (itemStack.count != itemStack.count || player.hasInfiniteMaterials())) {
                        mc.gameRenderer.itemInHandRenderer.itemUsed(hand)
                    }
                }
                return
            }
            
            if (interactionResult is InteractionResult.Fail) return
        }
    }
}
