package quoi.mixininterfaces;

import com.mojang.authlib.GameProfile;

public interface IGuiMessage {
    String quoi$getText();

    int quoi$getId();

    void quoi$setId(int id);

    GameProfile quoi$getSender();

    void quoi$setSender(GameProfile profile);
}