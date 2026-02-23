package quoi.mixins;

import quoi.module.impl.render.NickHider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(CommandSuggestions.SuggestionsList.class)
public class CommandSuggestionsListMixin {

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"
            ),
            index = 1
    )
    private String string(String string) {
        if (NickHider.INSTANCE.getEnabled()) {
            LocalPlayer player = Minecraft.getInstance().player;
            assert player != null;
            String name = player.getName().getString();
            if (string.equals(name)) return NickHider.INSTANCE.getCustomName();
        }
        return string;
    }
}
