package quoi.mixininterfaces;

import net.minecraft.network.chat.Component;

public interface ISearchMode {
    boolean quoi$isSearchActive();

    void quoi$queueMessage(Component message);
}
