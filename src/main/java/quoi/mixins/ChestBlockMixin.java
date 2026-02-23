package quoi.mixins;

import quoi.mixininterfaces.IOriginalCollisionShapeProvider;
import quoi.module.impl.dungeon.FullBlockHitboxes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Map;

@Mixin(ChestBlock.class)
public abstract class ChestBlockMixin implements IOriginalCollisionShapeProvider {

    @Shadow @Final private static VoxelShape SHAPE;
    @Shadow @Final private static Map<Direction, VoxelShape> HALF_SHAPES;

    @Shadow
    public static Direction getConnectedDirection(BlockState blockState) {
        throw new AbstractMethodError("Shadowed method");
    }

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void onGetShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (FullBlockHitboxes.getShouldExpandHitboxes()) {
            cir.setReturnValue(Shapes.block());
        }
    }

    @Override
    public VoxelShape quoi$getOriginalCollisionShape(BlockState state) {
        ChestType type = state.getValue(ChestBlock.TYPE);
        if (type == ChestType.SINGLE) {
            return SHAPE;
        } else {
            return HALF_SHAPES.get(getConnectedDirection(state));
        }
    }
}