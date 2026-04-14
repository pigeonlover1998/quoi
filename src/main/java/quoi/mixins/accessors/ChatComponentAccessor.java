package quoi.mixins.accessors;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
    @Accessor("allMessages")
    List<GuiMessage> getMessages();

    @Accessor("trimmedMessages")
    List<GuiMessage.Line> getVisibleMessages();

    @Invoker("getScale")
    double invokeGetScale();

    @Invoker("getLineHeight")
    int invokeGetLineHeight();

    @Invoker("getWidth")
    int invokeGetWidth();

    @Invoker
    void invokeRefreshTrimmedMessages();

    @Accessor("chatScrollbarPos")
    int getScrolledLines();

    @Accessor("chatScrollbarPos")
    void setScrolledLines(int value);
}
