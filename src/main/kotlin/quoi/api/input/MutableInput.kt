package quoi.api.input

import net.minecraft.world.entity.player.Input

data class MutableInput(
    var forward: Boolean = false,
    var backward: Boolean = false,
    var left: Boolean = false,
    var right: Boolean = false,
    var jump: Boolean = false,
    var shift: Boolean = false,
    var sprint: Boolean = false
) {
    constructor(input: Input) : this(
        input.forward,
        input.backward,
        input.left,
        input.right,
        input.jump,
        input.shift,
        input.sprint
    )

    fun toInput(): Input = Input(forward, backward, left, right, jump, shift, sprint)

    fun stop() {
        forward = false
        backward = false
        left = false
        right = false
    }

    fun invert() {
        val f = forward
        val b = backward
        val l = left
        val r = right
        forward = b
        backward = f
        left = r
        right = l
    }

    val moving: Boolean get() = forward != backward || left != right
}