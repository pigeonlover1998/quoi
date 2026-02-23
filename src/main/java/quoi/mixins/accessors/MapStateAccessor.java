package quoi.mixins.accessors;

import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(MapItemSavedData.class)
public interface MapStateAccessor {
    @Accessor("decorations")
    Map<String, MapDecoration> getDecorations();
}
