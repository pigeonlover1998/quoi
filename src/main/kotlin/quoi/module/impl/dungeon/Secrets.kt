package quoi.module.impl.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.entity.item.ItemEntity
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.ChatEvent
import quoi.api.events.DungeonEvent
import quoi.api.events.RenderEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.dungeon.Dungeon.dungeonItemDrops
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.EntityUtils.interpolatedBox
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.StringUtils.containsOneOf
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.aabb
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawWireFrameBox
import quoi.utils.skyblock.player.PlayerUtils
import java.util.concurrent.CopyOnWriteArrayList

// https://github.com/Noamm9/CatgirlAddons/blob/main/src/main/kotlin/catgirlroutes/module/impl/dungeons/Secrets.kt
object Secrets : Module(
    "Secrets",
    desc = "Highlights collected secrets."
) {
    private val secretChime by switch("Chime", desc = "Plays a sound on secret click.")
    private val clickSound = sound("Secret").childOf(::secretChime)
//    private val dropSound = createSoundSettings("Drop", chimeDropdown) { secretChime }

    private val secretClicks by switch("Clicks", desc = "Highlights the secret on click.")
    private val outline by switch("Outline", desc = "Draws the outline.").childOf(::secretClicks)
    private val thickness by slider("Thickness", 3f, 1f, 6f).childOf(::outline)
    private val clickColour by colourPicker("Click colour", Colour.GREEN, allowAlpha = true).childOf(::secretClicks)
    private val lockedColour by colourPicker("Locked colour", Colour.RED, allowAlpha = true, desc = "Locked secret colour.").childOf(::secretClicks)

    private val itemHighlight by switch("Item", desc = "Highlights secret items.")
    private val closeColour by colourPicker("Close colour", Colour.GREEN, allowAlpha = true, desc = "Highlight colour when the player is near the item.").childOf(::itemHighlight)
    private val farColour by colourPicker("Far colour", Colour.RED, allowAlpha = true, desc = "Highlight colour when the player is far from the item.").childOf(::itemHighlight)
    private val sizeOffset by slider("Size offset", 0.0, -1.0, 1.0, 0.05, desc = "Changes box size offset.").childOf(::itemHighlight)
    private val playSound by switch("Play sound", desc = "Plays a sound when the player is near the item.").childOf(::itemHighlight)
    private val itemSound = sound("Item").childOf(::itemHighlight) { playSound }

    private data class Secret(val blockPos: BlockPos, var isLocked: Boolean = false)
    private val clickedSecrets = CopyOnWriteArrayList<Secret>()
    private var lastPlayed = System.currentTimeMillis()
    private val itemEntities = CopyOnWriteArrayList<ItemEntity>()

    init {
        on<DungeonEvent.Secret.Interact> {
            secretHighlight(blockPos)
            playSecretSound(clickSound)
        }

        on<DungeonEvent.Secret.Item> {
            playSecretSound(clickSound) // dropSound
        }

        on<DungeonEvent.Secret.Bat> {
            playSecretSound(clickSound) // dropSound?
        }

        on<ChatEvent.Packet> {
            if (secretClicks && message.noControlCodes == "That chest is locked!") {
                clickedSecrets.lastOrNull()?.isLocked = true
            }
        }

        on<RenderEvent.World> {
            clickedSecrets.forEach { (blockPos, locked) ->
                val colour = if (locked) lockedColour else clickColour
                val aabb = blockPos.aabb
                ctx.drawFilledBox(aabb, colour)
                if (outline && colour.alpha != 1.0f) ctx.drawWireFrameBox(aabb, colour.withAlpha(1.0f), thickness = thickness)
            }
            itemEntities.removeIf { item ->
                if (!item.isAlive) return@removeIf true
                var colour = farColour

                if (item.distanceTo(player) <= 3.5) {
                    if (playSound) PlayerUtils.playSound(itemSound)
                    colour = closeColour
                }
                ctx.drawFilledBox(item.interpolatedBox.inflate(sizeOffset), colour)
                false
            }
        }

        on<RenderEvent.Entity> {
            if (!itemHighlight) return@on
            val itemEntity = entity as? ItemEntity ?: return@on
            if (itemEntity.item?.hoverName?.string?.containsOneOf(dungeonItemDrops, true) == true) {
                itemEntities.addIfAbsent(itemEntity)
                cancel()
            }
        }

        on<WorldEvent.Change> {
            clickedSecrets.clear()
            itemEntities.clear()
        }
    }

    private fun playSecretSound(sound: () -> Triple<SoundEvent, Float, Float>) {
        if (System.currentTimeMillis() - lastPlayed > 10 && secretChime) {
            PlayerUtils.playSound(sound)
            lastPlayed = System.currentTimeMillis()
        }
    }

    private fun secretHighlight(blockPos: BlockPos) {
        if (!secretClicks || clickedSecrets.any { it.blockPos == blockPos }) return
        clickedSecrets.add(Secret(blockPos))
        scheduleTask(20) { clickedSecrets.removeFirstOrNull() }
    }
}