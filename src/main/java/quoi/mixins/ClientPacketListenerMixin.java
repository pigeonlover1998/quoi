package quoi.mixins;

import quoi.api.events.ChatEvent;
import quoi.api.events.core.EventDispatcher;
import quoi.module.impl.general.Tweaks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static quoi.module.impl.render.RenderOptimiser.should;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    // based on https://github.com/Noamm9/NoammAddons/blob/1.1.9/src/main/java/com/github/noamm9/mixin/MixinClientPacketListener.java
    @WrapOperation(
            method = "handleBundlePacket",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"
            )
    )
    private void quoi$handleBundledPacket(Packet<?> packet, PacketListener listener, Operation<Void> original) {
        if (EventDispatcher.onPacketReceived(packet)) return;
        original.call(packet, listener);
    }

    @Inject(
            method = "sendChat(Ljava/lang/String;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onSendChatMessage(String message, CallbackInfo ci) {
        if (new ChatEvent.Sent(message, false).post()) ci.cancel();
    }

    @Inject(
            method = "sendCommand",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onSendChatCommand(String command, CallbackInfo ci) {
        if (new ChatEvent.Sent(command, true).post()) ci.cancel();
    }

    @Inject(
            method = "handleSetEntityData",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/syncher/SynchedEntityData;assignValues(Ljava/util/List;)V"
            )
    )
    private void onEntityTrackerUpdate(ClientboundSetEntityDataPacket packet, CallbackInfo ci, @Local Entity entity) {
        if(!entity.equals(Minecraft.getInstance().player) || !should(Tweaks.getFixDoubleSneak())) return;
        packet.packedItems().removeIf(entry -> entry.serializer().equals(EntityDataSerializers.POSE));
    }
}
