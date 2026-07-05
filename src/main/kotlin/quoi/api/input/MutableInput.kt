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

    fun invert() = MutableInput(
        backward,
        forward,
        right,
        left,
        jump, shift, sprint
    )

    val moving: Boolean get() = forward != backward || left != right
}