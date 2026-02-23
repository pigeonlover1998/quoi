package quoi.utils.ui.screens

import quoi.QuoiMod.mc
import quoi.api.abobaui.AbobaUI
import quoi.api.input.CatKeyboard.Modifier.isCtrlDown
import quoi.api.input.CatKeys
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.sf
import quoi.utils.ui.rendering.NVGSpecialRenderer
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

class UIScreen(val instance: AbobaUI.Instance) : Screen(Component.literal(instance.title)) {

    override fun init() {
        instance.init(width * sf, height * sf)
    }

    override fun render(ctx: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        instance.ctx = ctx
        instance.eventManager.onMouseMove(mouseX * sf.toFloat(), mouseY * sf.toFloat())
        NVGSpecialRenderer.draw(ctx, 0, 0, ctx.guiWidth(), ctx.guiHeight()) {
            instance.render()
        }
        super.render(ctx, mouseX, mouseY, deltaTicks)
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, doubled: Boolean) =
        instance.eventManager.onMouseClick(mouseButtonEvent.button())

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        instance.eventManager.onMouseRelease(mouseButtonEvent.button())
        return super.mouseReleased(mouseButtonEvent)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        val a = instance.eventManager.onKeyTyped(keyEvent.key)

        val ctrlHotkeys = setOf(
            CatKeys.KEY_V,
            CatKeys.KEY_C,
            CatKeys.KEY_W,
            CatKeys.KEY_X,
            CatKeys.KEY_A
        )
        var b = false
        if (isCtrlDown && keyEvent.key in ctrlHotkeys) {
            b = instance.eventManager.onKeyTyped(keyEvent.key.toChar())
        }
        return a || b || super.keyPressed(keyEvent)
    }

    override fun keyReleased(keyEvent: KeyEvent) = instance.eventManager.onKeyReleased(keyEvent.key)

    override fun charTyped(characterEvent: CharacterEvent) =
        if (characterEvent.isAllowedChatCharacter) instance.eventManager.onKeyTyped(characterEvent.codepoint.toChar()) else false

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double) =
        instance.eventManager.onMouseScroll(verticalAmount.toFloat())

    override fun onClose() {
        instance.close()
        super.onClose()
    }

    override fun isPauseScreen() = false

//    override fun renderBackground(guiGraphics: GuiGraphics, mouseY: Int, j: Int, deltaTicks: Float) {  }

    companion object {
        fun open(ui: AbobaUI.Instance) = scheduleTask { mc.setScreen(UIScreen(ui)) }
    }
}