package quoi.mixins;

import quoi.api.events.ChatEvent;
import quoi.mixininterfaces.IGuiMessage;
import quoi.mixininterfaces.IChatComponent;
import quoi.mixininterfaces.ISearchMode;
import quoi.module.impl.misc.Chat;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin implements IChatComponent {
    @Shadow
    @Final
    private List<GuiMessage.Line> trimmedMessages;
    @Shadow
    @Final
    private List<GuiMessage> allMessages;

    @Shadow
    public abstract void addMessage(Component message, @Nullable MessageSignature signatureData, @Nullable GuiMessageTag indicator);
    @Shadow
    public abstract void addMessage(Component message);

    @Unique
    private int nextId;

    @Override
    public void quoi$add(Component message, int id) {

        if (id != 0) {
            trimmedMessages.removeIf(msg -> ((IGuiMessage) (Object) msg).quoi$getId() == id);
            allMessages.removeIf(msg -> ((IGuiMessage) (Object) msg).quoi$getId() == id);
        }

        nextId = id;
        addMessage(message);
        nextId = 0;
    }

    @ModifyArg(
            method = "addMessageToDisplayQueue",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;add(ILjava/lang/Object;)V"
            ),
            index = 1
    )
    private Object onAddVisibleLine(Object line) {
        if (nextId != 0) {
            ((IGuiMessage) line).quoi$setId(nextId);
        }
        return line;
    }

    @Inject(
            method = "addMessageToQueue(Lnet/minecraft/client/GuiMessage;)V",
            at = @At("TAIL")
    )
    private void onAddMessageAfterNewLine(GuiMessage message, CallbackInfo ci) {
        if (nextId != 0 && !allMessages.isEmpty()) {
            ((IGuiMessage) (Object) allMessages.getFirst()).quoi$setId(nextId);
        }
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onAddMessage(Component message, MessageSignature signatureData, GuiMessageTag indicator, CallbackInfo ci) {
        if (new ChatEvent.Receive(message.getString(), message, nextId).post()) ci.cancel();
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("TAIL"),
            cancellable = true
    )
    private void onAddMessagePost(Component message, MessageSignature signatureData, GuiMessageTag indicator, CallbackInfo ci) {
        if (new ChatEvent.Receive.Post(message.getString(), message, nextId).post()) ci.cancel();
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void interceptMessage(Component message, CallbackInfo ci) {
        Screen currentScreen = Minecraft.getInstance().screen;
        if (currentScreen instanceof ISearchMode searchScreen) {
            if (searchScreen.quoi$isSearchActive()) {
                searchScreen.quoi$queueMessage(message);
                ci.cancel();
            }
        }
    }

    @ModifyVariable(
            method = "render",
            at = @At("HEAD"),
            argsOnly = true
    )
    private boolean renderFocused(boolean focused) {
        return focused || Chat.INSTANCE.isDown();
    }

    @ModifyExpressionValue(
            method = {"getHeight()I", "addMessageToDisplayQueue"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent;isChatFocused()Z"
            )
    )
    private boolean focusWhenPeeking(boolean original) {
        return original || Chat.INSTANCE.isDown();
    }

}