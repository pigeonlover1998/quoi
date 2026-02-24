package quoi.module.impl.dungeon

import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.ChatEvent
import quoi.api.events.DungeonEvent
import quoi.api.events.RenderEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.dungeon.Dungeon.dungeonItemDrops
import quoi.module.Module
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.ColourSetting
import quoi.module.settings.impl.DropdownSetting
import quoi.module.settings.impl.NumberSetting
import quoi.utils.EntityUtils.interpolatedBox
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.StringUtils.containsOneOf
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.aabb
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawWireFrameBox
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.ui.createSoundSettings
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.entity.item.ItemEntity
import java.util.concurrent.CopyOnWriteArrayList

// https://github.com/Noamm9/CatgirlAddons/blob/main/src/main/kotlin/catgirlroutes/module/impl/dungeons/Secrets.kt
object Secrets : Module(
    "Secrets",
    desc = "Highlights collected secrets."
) {
    private val chimeDropdown by DropdownSetting("Chime").collapsible()
    private val secretChime by BooleanSetting("Secret chime", desc = "Plays a sound on secret click.").withDependency(chimeDropdown)
    private val clickSound = createSoundSettings("Secret", chimeDropdown) { secretChime }
//    private val dropSound = createSoundSettings("Drop", chimeDropdown) { secretChime }

    private val highlightDropdown by DropdownSetting("Highlight").collapsible()
    private val secretClicks by BooleanSetting("Secret clicks", desc = "Highlights the secret on click.").withDependency(highlightDropdown)
    private val outline by BooleanSetting("Outline", desc = "Draws the outline.").withDependency(highlightDropdown) { secretClicks }
    private val clickColour by ColourSetting("Click colour", Colour.GREEN, allowAlpha = true).withDependency(highlightDropdown) { secretClicks }
    private val lockedColour by ColourSetting("Locked colour", Colour.RED, allowAlpha = true, desc = "Locked secret colour.").withDependency(highlightDropdown){ secretClicks }

    private val itemDropdown by DropdownSetting("Item").collapsible()
    private val itemHighlight by BooleanSetting("Item highlight", desc = "Highlights secret items.").withDependency(itemDropdown)
    private val closeColour by ColourSetting("Close colour", Colour.GREEN, allowAlpha = true, desc = "Highlight colour when the player is near the item.").withDependency(itemDropdown) { itemHighlight }
    private val farColour by ColourSetting("Far colour", Colour.RED, allowAlpha = true, desc = "Highlight colour when the player is far from the item.").withDependency(itemDropdown) { itemHighlight }
    private val sizeOffset by NumberSetting("Size offset", 0.0, -1.0, 1.0, 0.05, desc = "Changes box size offset.").withDependency(itemDropdown) { itemHighlight }
    private val playSound by BooleanSetting("Play sound", desc = "Plays a sound when the player is near the item.").withDependency(itemDropdown) { itemHighlight }
    private val itemSound = createSoundSettings("Item", itemDropdown) { itemHighlight && playSound }

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
                if (outline && colour.alpha != 1.0f) ctx.drawWireFrameBox(aabb, colour.withAlpha(1.0f))
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