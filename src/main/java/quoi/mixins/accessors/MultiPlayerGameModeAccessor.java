package quoi.mixins.accessors;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
    @Invoker("startPrediction")
    void invokeStartPrediction(ClientLevel level, PredictiveAction action);
    
    @Invoker("ensureHasSentCarriedItem")
    void invokeEnsureHasSentCarriedItem();
}
