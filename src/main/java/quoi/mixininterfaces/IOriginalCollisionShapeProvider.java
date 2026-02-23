package quoi.mixininterfaces;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface IOriginalCollisionShapeProvider {
    VoxelShape quoi$getOriginalCollisionShape(BlockState state);
}