package quoi.api.input

import quoi.QuoiMod.mc
import org.lwjgl.glfw.GLFW

object CatMouse {

    fun getButtonName(code: Int): String {
        return when (code) {
            0 -> "Mouse Left"
            1 -> "Mouse Right"
            2 -> "Mouse Middle"
            3 -> "Mouse 4"
            4 -> "Mouse 5"
            5 -> "Mouse 6"
            6 -> "Mouse 7"
            7 -> "Mouse 8"
            else -> "Unknown"
        }
    }

    fun isButtonDown(code: Int): Boolean {
        val state = GLFW.glfwGetMouseButton(mc.window.handle(), code)
        return state == GLFW.GLFW_PRESS || state == GLFW.GLFW_REPEAT
    }

    val mx: Float get() = mc.mouseHandler.xpos().toFloat()

    val my: Float get() = mc.mouseHandler.ypos().toFloat()

    fun setCursor(cursor: Long) {
        GLFW.glfwSetCursor(mc.window.handle(), cursor)
    }
}