package quoi.mixins;

import org.spongepowered.asm.mixin.injection.*;
import quoi.api.events.ChatEvent;
import quoi.mixininterfaces.IGuiMessage;
import quoi.mixininterfaces.IChatComponent;
import quoi.mixininterfaces.ISearchMode;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import quoi.module.impl.misc.chat.impl.ChatPeek;
import quoi.module.impl.misc.chat.impl.KeepChatHistory;
import quoi.module.impl.misc.chat.impl.NoChatLimit;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin implements IChatComponent {
    @Shadow
    @Final
    private List<GuiMessage.Line> trimmedMessages;
    @Shadow
    @Final
    private List<GuiMessage> allMessages;

    @Shadow
    public abstract void addClientSystemMessage(Component message);

    @Unique
    private int nextId;

    @Override
    public void quoi$add(Component message, int id) {

        if (id != 0) {
            trimmedMessages.removeIf(msg -> ((IGuiMessage) (Object) msg).quoi$getId() == id);
            allMessages.removeIf(msg -> ((IGuiMessage) (Object) msg).quoi$getId() == id);
        }

        nextId = id;
        addClientSystemMessage(message);
        nextId = 0;
    }

    @ModifyArg(
            method = "addMessageToDisplayQueue",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;addFirst(Ljava/lang/Object;)V"
            ),
            index = 0
    )
    private Object onAddVisibleLine(Object line) {
        if (nextId != 0) {
            ((IGuiMessage) line).quoi$setId(nextId);
        }
        return line;
    }

    @Inject(
            method = "addMessageToQueue",
            at = @At("TAIL")
    )
    private void onAddMessageAfterNewLine(GuiMessage message, CallbackInfo ci) {
        if (nextId != 0 && !allMessages.isEmpty()) {
            ((IGuiMessage) (Object) allMessages.getFirst()).quoi$setId(nextId);
        }
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onAddMessage(Component message, MessageSignature signatureData, GuiMessageSource source, GuiMessageTag indicator, CallbackInfo ci) {
        if (new ChatEvent.Receive(message.getString(), message, nextId).post()) ci.cancel();
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("TAIL"),
            cancellable = true
    )
    private void onAddMessagePost(Component message, MessageSignature signatureData, GuiMessageSource source, GuiMessageTag indicator, CallbackInfo ci) {
        if (new ChatEvent.Receive.Post(message.getString(), message, nextId).post()) ci.cancel();
    }

    @Inject(
            method = "addClientSystemMessage(Lnet/minecraft/network/chat/Component;)V",
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
            method = "extractRenderState*",
            at = @At("HEAD"),
            argsOnly = true
    )
    private ChatComponent.DisplayMode renderFocused(ChatComponent.DisplayMode mode) {
        return ChatPeek.displayMode(mode);
    }

    @ModifyExpressionValue(
            method = {"getHeight()I", "addMessageToDisplayQueue"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent;isChatFocused()Z"
            )
    )
    private boolean focusWhenPeeking(boolean original) {
        return original || ChatPeek.isDown();
    }

    // from: https://github.com/jcnlk/quoi/blob/ac3574b5370e8d8327c65dcca81245f33afb2eab/src/main/java/quoi/mixins/ChatComponentMixin.java#L169
    @ModifyExpressionValue(
            method = {
                    "addMessageToDisplayQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V",
                    "addMessageToQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;size()I"
            ),
            slice = @Slice(
                    from = @At(value = "INVOKE", target = "Ljava/util/List;addFirst(Ljava/lang/Object;)V"),
                    to = @At(value = "INVOKE", target = "Ljava/util/List;removeLast()Ljava/lang/Object;")
            ),
            require = 2,
            expect = 2
    )
    private int applyNoChatLimit(int size) {
        return NoChatLimit.keepChat() ? 0 : size;
    }

    // from: https://github.com/jcnlk/quoi/blob/ac3574b5370e8d8327c65dcca81245f33afb2eab/src/main/java/quoi/mixins/ChatComponentMixin.java#L178
    @Inject(
            method = "clearMessages(Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void keepChatHistory(boolean clearRecentChat, CallbackInfo ci) {
        if (clearRecentChat && KeepChatHistory.keepsChat()) {
            ci.cancel();
        }
    }

}