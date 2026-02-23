package quoi.mixins;

import quoi.module.impl.dungeon.FullBlockHitboxes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ButtonBlock.class)
public class ButtonBlockMixin {

    @Unique
    @Final
    private static final VoxelShape FLOOR_SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.125, 1.0); // Flat on floor
    @Unique
    @Final
    private static final VoxelShape CEILING_SHAPE = Shapes.box(0.0, 0.875, 0.0, 1.0, 1.0, 1.0); // Flat on ceiling
    @Unique
    @Final
    private static final VoxelShape NORTH_SHAPE = Shapes.box(0.0, 0.0, 0.875, 1.0, 1.0, 1.0); // Flat on North wall
    @Unique
    @Final
    private static final VoxelShape SOUTH_SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 0.125); // Flat on South wall
    @Unique
    @Final
    private static final VoxelShape WEST_SHAPE = Shapes.box(0.875, 0.0, 0.0, 1.0, 1.0, 1.0); // Flat on West wall
    @Unique
    @Final
    private static final VoxelShape EAST_SHAPE = Shapes.box(0.0, 0.0, 0.0, 0.125, 1.0, 1.0); // Flat on East wall

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void onGetShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (FullBlockHitboxes.getShouldExpandHitboxes()) {
            AttachFace face = state.getValue(ButtonBlock.FACE);

            switch (face) {
                case FLOOR:
                    cir.setReturnValue(FLOOR_SHAPE);
                    break;
                case CEILING:
                    cir.setReturnValue(CEILING_SHAPE);
                    break;
                case WALL:
                    switch (state.getValue(ButtonBlock.FACING)) {
                        case NORTH: cir.setReturnValue(NORTH_SHAPE); break;
                        case SOUTH: cir.setReturnValue(SOUTH_SHAPE); break;
                        case WEST:  cir.setReturnValue(WEST_SHAPE); break;
                        case EAST:  cir.setReturnValue(EAST_SHAPE); break;
                    }
                    break;
            }
        }
    }
}