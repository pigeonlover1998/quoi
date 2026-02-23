package quoi.mixins;

import quoi.mixininterfaces.IGuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(net.minecraft.client.GuiMessage.class)
public abstract class GuiMessage implements IGuiMessage {
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
