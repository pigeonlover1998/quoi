package quoi.api.world

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

data class RaycastResult(val succeeded: Boolean, val pos: BlockPos?, val state: BlockState?) {
    private var _vec3: Vec3? = null

    var vec: Vec3
        get() {
            if (_vec3 == null) {
                _vec3 = Vec3(pos ?: BlockPos.ZERO)
            }
            return _vec3!!
        }
        set(value) {
            _vec3 = value
        }

    companion object {
        val NONE = RaycastResult(false, null, null)
    }
}