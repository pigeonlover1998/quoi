package quoi.utils

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.GuiMessage
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.components.ImageButton
import net.minecraft.client.gui.components.WidgetSprites
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.multiplayer.prediction.PredictiveAction
import net.minecraft.util.Mth
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import quoi.QuoiMod.mc
import quoi.mixininterfaces.IChatComponent
import quoi.mixininterfaces.IGuiMessage
import quoi.mixins.accessors.ChatComponentAccessor
import quoi.mixins.accessors.InventoryAccessor
import quoi.mixins.accessors.KeyMappingAccessor
import quoi.mixins.accessors.MultiPlayerGameModeAccessor
import quoi.mixins.accessors.ImageButtonAccessor

fun MultiPlayerGameMode.startPrediction(action: PredictiveAction) {
    val level = mc.level ?: return
    (this as MultiPlayerGameModeAccessor).invokeStartPrediction(level, action)
}

inline val ChatComponent.messages: MutableList<GuiMessage>
    get() = (this as ChatComponentAccessor).messages

inline val ChatComponent.visibleMessages: List<GuiMessage.Line>
    get() = (this as ChatComponentAccessor).visibleMessages

inline val ChatComponent.chatWidth: Int
    get() = (this as ChatComponentAccessor).invokeGetWidth()

inline val ChatComponent.chatScale: Double
    get() = (this as ChatComponentAccessor).invokeGetScale()

inline val ChatComponent.chatLineHeight: Int
    get() = (this as ChatComponentAccessor).invokeGetLineHeight()

fun ChatComponent.toChatLineMX(x: Double): Double =
    x / chatScale - 4.0

fun ChatComponent.toChatLineMY(y: Double): Double {
    val chatY = mc.window.guiScaledHeight - y - 40.0
    return chatY / (chatScale * chatLineHeight)
}

fun ChatComponent.getMessageLineIdx(chatLineX: Double, chatLineY: Double): Int {
    if (!isChatFocused || visibleMessages.isEmpty()) return -1

    val maxChatX = Mth.floor(chatWidth / chatScale.toFloat()).toDouble()
    if (chatLineX < -4.0 || chatLineX > maxChatX) return -1

    val maxLine = minOf(linesPerPage, visibleMessages.size)
    if (chatLineY < 0.0 || chatLineY >= maxLine.toDouble()) return -1

    val idx = Mth.floor(chatLineY + scrolledLines.toDouble())
    return idx.takeIf { it in visibleMessages.indices } ?: -1
}

fun ChatComponent.refreshTrimmedMessages() =
    (this as ChatComponentAccessor).invokeRefreshTrimmedMessages()

inline var ChatComponent.scrolledLines: Int
    get() = (this as ChatComponentAccessor).scrolledLines
    set(value) {
        (this as ChatComponentAccessor).scrolledLines = value
    }

fun ChatComponent.add(text: Component, id: Int) =
    (this as IChatComponent).`quoi$add`(text, id)

@Suppress("CAST_NEVER_SUCCEEDS")
inline var GuiMessage.id: Int
    get() = (this as IGuiMessage).`quoi$getId`()
    set(value) {
        (this as IGuiMessage).`quoi$setId`(value)
    }

inline val Inventory.items: List<ItemStack>
    get() = (this as InventoryAccessor).items

inline val KeyMapping.key: InputConstants.Key
    get() = (this as KeyMappingAccessor).key

inline val ImageButton.textures: WidgetSprites
    get() = (this as ImageButtonAccessor).textures

