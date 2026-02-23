package quoi.utils.ui

import quoi.QuoiMod.mc
import org.lwjgl.glfw.GLFW

object MouseUtils {

    val mx: Float get() = mc.mouseHandler.xpos().toFloat()

    val my: Float get() = mc.mouseHandler.ypos().toFloat()

    fun setCursor(cursor: Long) {
        GLFW.glfwSetCursor(mc.window.handle(), cursor)
    }
}