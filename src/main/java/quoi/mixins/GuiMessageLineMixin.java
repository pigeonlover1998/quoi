package quoi.mixins;

import quoi.mixininterfaces.IGuiMessage;
import net.minecraft.client.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

// https://github.com/MeteorDevelopment/meteor-client/blob/master/src/main/java/meteordevelopment/meteorclient/mixin/ChatHudLineVisibleMixin.java

@Mixin(GuiMessage.Line.class)
public abstract class GuiMessageLineMixin implements IGuiMessage {
    @Unique
    private int quoi$id;

    @Override
    public int quoi$getId() {
        return quoi$id;
    }

    @Override
    public void quoi$setId(int id) {
        this.quoi$id = id;
    }
}