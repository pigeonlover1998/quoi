package quoi.mixins;

import quoi.mixininterfaces.IOriginalCollisionShapeProvider;
import quoi.module.impl.dungeon.FullBlockHitboxes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SkullBlock.class)
public class SkullBlockMixin implements IOriginalCollisionShapeProvider {

    @Shadow @Final private static VoxelShape SHAPE;

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void onGetShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (FullBlockHitboxes.getShouldExpandHitboxes()) {
            cir.setReturnValue(Shapes.block());
        }
    }

    @Override
    public VoxelShape quoi$getOriginalCollisionShape(BlockState state) {
        return SHAPE;
    }
}