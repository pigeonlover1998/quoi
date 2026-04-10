package quoi.api.input

import net.minecraft.world.entity.player.Input

/**
 * from rsm (BSD 3-Clause)
 * copyright (c) 2026 ricedotwho
 * original: https://github.com/rs-mod/rsm/blob/main/src/main/java/com/ricedotwho/rsm/data/MutableInput.java
 */
class MutableInput(private var inputs: Int = 0) {
    var modified: Boolean = false
        private set

    constructor(input: Input) : this(fromInput(input))
    constructor(b: Byte) : this(b.toInt())

    fun apply(input: Input) {
        inputs = fromInput(input)
    }

    fun toInput(): Input {
        return Input(forward, backward, left, right, jump, shift, sprint)
    }

    var forward: Boolean
        get() = (inputs and FLAG_FORWARD) != 0
        set(v) = update(FLAG_FORWARD, v)

    var backward: Boolean
        get() = (inputs and FLAG_BACKWARD) != 0
        set(v) = update(FLAG_BACKWARD, v)

    var left: Boolean
        get() = (inputs and FLAG_LEFT) != 0
        set(v) = update(FLAG_LEFT, v)

    var right: Boolean
        get() = (inputs and FLAG_RIGHT) != 0
        set(v) = update(FLAG_RIGHT, v)

    var jump: Boolean
        get() = (inputs and FLAG_JUMP) != 0
        set(v) = update(FLAG_JUMP, v)

    var shift: Boolean
        get() = (inputs and FLAG_SHIFT) != 0
        set(v) = update(FLAG_SHIFT, v)

    var sprint: Boolean
        get() = (inputs and FLAG_SPRINT) != 0
        set(v) = update(FLAG_SPRINT, v)

    private fun update(flag: Int, v: Boolean) {
        inputs = if (v) inputs or flag else inputs and flag.inv()
        modified = true
    }


    companion object {
        private const val FLAG_FORWARD  = 0x01
        private const val FLAG_BACKWARD = 0x02
        private const val FLAG_LEFT     = 0x04
        private const val FLAG_RIGHT    = 0x08
        private const val FLAG_JUMP     = 0x10
        private const val FLAG_SHIFT    = 0x20
        private const val FLAG_SPRINT   = 0x40

        private fun fromInput(input: Input): Int {
            var ret = 0
            if (input.forward()) ret = ret or FLAG_FORWARD
            if (input.backward()) ret = ret or FLAG_BACKWARD
            if (input.left()) ret = ret or FLAG_LEFT
            if (input.right()) ret = ret or FLAG_RIGHT
            if (input.jump()) ret = ret or FLAG_JUMP
            if (input.shift()) ret = ret or FLAG_SHIFT
            if (input.sprint()) ret = ret or FLAG_SPRINT
            return ret
        }
    }
}