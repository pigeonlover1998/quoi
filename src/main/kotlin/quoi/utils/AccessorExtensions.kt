package quoi.utils

import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.multiplayer.prediction.PredictiveAction
import quoi.QuoiMod.mc
import quoi.mixins.accessors.MultiPlayerGameModeAccessor

// todo add the rest of the stuff
fun MultiPlayerGameMode.startPrediction(action: PredictiveAction) {
    val level = mc.level ?: return
    (this as MultiPlayerGameModeAccessor).invokeStartPrediction(level, action)
}


