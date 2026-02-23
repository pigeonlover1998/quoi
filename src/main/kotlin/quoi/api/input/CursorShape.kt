package quoi.api.input

import org.lwjgl.glfw.GLFW

object CursorShape {
    val ARROW by lazy { GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR) }
    val HAND by lazy { GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR) }
    val IBEAM by lazy { GLFW.glfwCreateStandardCursor(GLFW.GLFW_IBEAM_CURSOR) }
    val CROSSHAIR by lazy { GLFW.glfwCreateStandardCursor(GLFW.GLFW_CROSSHAIR_CURSOR) }
    val HRESIZE by lazy { GLFW.glfwCreateStandardCursor(GLFW.GLFW_HRESIZE_CURSOR) }
    val VRESIZE by lazy { GLFW.glfwCreateStandardCursor(GLFW.GLFW_VRESIZE_CURSOR) }
    const val NORMAL = 0L
}