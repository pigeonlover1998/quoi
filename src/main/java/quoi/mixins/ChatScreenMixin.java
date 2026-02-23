package quoi.mixins;


import quoi.api.input.CatKeyboard;
import quoi.api.input.CatKeys;
import quoi.mixininterfaces.ISearchMode;
import quoi.mixins.accessors.ChatComponentAccessor;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.*;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

@Mixin(ChatScreen.class)
public class ChatScreenMixin extends Screen implements ISearchMode {
    protected ChatScreenMixin(Component title) { super(title); }

    @Unique
    private static boolean isSearchActive = false;
    @Unique
    private static final List<GuiMessage> messageBackup = new ObjectArrayList<>();
    @Unique
    private static final List<Component> queuedMessages = new ObjectArrayList<>();
    @Unique
    private static String textBeforeSearch = "";

    @Shadow
    protected EditBox input;

    @Unique
    @NotNull
    private final Minecraft mc = Minecraft.getInstance();

//    @Inject(
//            method = "sendMessage",
//            at = @At("HEAD"),
//            cancellable = true
//    )
//    private void onSendMessage(String message, boolean addToHistory, CallbackInfo ci) {
//        if (EventBus.INSTANCE.post(new ChatEvent.Sent(message))) ci.cancel();
//    }

    @Inject(
            method = "keyPressed",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onKeyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (CatKeyboard.Modifier.INSTANCE.isCtrlDown() && input.input() == CatKeys.KEY_F) {
            toggleSearch(!isSearchActive);
            cir.setReturnValue(true);
            return;
        }

        if (isSearchActive && input.input() == CatKeys.KEY_ESCAPE) {
            toggleSearch(false);
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "onEdited",
            at = @At("TAIL")
    )
    private void onChatInput(String text, CallbackInfo ci) {
        if (isSearchActive) {
            doSearch(text);
        }
    }

    @Inject(
            method = "removed",
            at = @At("TAIL")
    )
    private void onRemoved(CallbackInfo ci) {
        if (isSearchActive) toggleSearch(false);
    }

    @Unique
    private void toggleSearch(boolean activate) {
        isSearchActive = activate;
        ChatComponent chatHud = mc.gui.getChat();
        ChatComponentAccessor accessor = (ChatComponentAccessor) chatHud;

        if (activate) {
            messageBackup.clear();
            messageBackup.addAll(accessor.getMessages());
            queuedMessages.clear();

            textBeforeSearch = input.getValue();
            input.setValue("");
            input.setTextColor(Color.YELLOW.getRGB());
            doSearch("");
        } else {
            accessor.getMessages().clear();
            accessor.getMessages().addAll(messageBackup);
            messageBackup.clear();

            for (Component queuedMessage : queuedMessages) {
                chatHud.addMessage(queuedMessage);
            }
            queuedMessages.clear();

            input.setValue(textBeforeSearch);
            input.setTextColor(-2039584);

            chatHud.rescaleChat();
        }
    }

    @Unique
    private void doSearch(String query) {
        ChatComponent chatHud = mc.gui.getChat();
        ChatComponentAccessor accessor = (ChatComponentAccessor) chatHud;
        List<GuiMessage> messages = accessor.getMessages();

        messages.clear();

        if (query.isEmpty()) {
            messages.addAll(messageBackup);
        } else {
            List<GuiMessage> filteredResults;
            try {
                Pattern pattern = Pattern.compile(query, Pattern.CASE_INSENSITIVE);
                filteredResults = messageBackup.stream()
                        .filter(msg -> pattern.matcher(msg.content().getString()).find())
                        .toList();
            } catch (PatternSyntaxException e) {
                filteredResults = messageBackup.stream()
                        .filter(msg -> msg.content().getString().toLowerCase().contains(query.toLowerCase()))
                        .toList();
            }

            messages.addAll(filteredResults);
        }
        messages.addFirst(new GuiMessage(mc.gui.getGuiTicks(), Component.literal("§e§lSEARCH ON"), null, null));
        chatHud.rescaleChat();
    }

    @Override
    public boolean quoi$isSearchActive() {
        return isSearchActive;
    }

    @Override
    public void quoi$queueMessage(Component message) {
        queuedMessages.add(message);
    }
}
