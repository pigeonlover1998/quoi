package quoi.api.events

import quoi.api.events.core.CancellableEvent
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.inventory.ClickType

abstract class GuiEvent {
    abstract class Open {
        class Pre(val screen: Screen) : CancellableEvent()
        class Post(val screen: Screen) : CancellableEvent()
    }

    class Close(val screen: Screen) : CancellableEvent()

    class Click(val screen: Screen, val mx: Double, val my: Double, val button: Int, val state: Boolean) : CancellableEvent()
    class Key(val screen: Screen, val key: Int) : CancellableEvent()
    class Char(val screen: Screen, val char: kotlin.Char) : CancellableEvent()

    class Draw(val screen: Screen, val ctx: GuiGraphics, val mx: Int, val my: Int) : CancellableEvent()
    class DrawBackground(val screen: Screen, val ctx: GuiGraphics, val mx: Int, val my: Int) : CancellableEvent()

    abstract class Slot {
        class Click(val screen: Screen, val slot: net.minecraft.world.inventory.Slot, val slotId: Int, val button: Int, val actionType: ClickType) : CancellableEvent()
        class Draw(val screen: Screen, val ctx: GuiGraphics, val slot: net.minecraft.world.inventory.Slot) : CancellableEvent()
    }

    class DrawTooltip(val screen: Screen, val ctx: GuiGraphics, val mouseX: Int, val mouseY: Int) : CancellableEvent()
}