package quoi.mixins;

import quoi.api.events.RenderEvent;
import quoi.module.impl.misc.ChatReplacements;
import quoi.module.impl.player.PlayerDisplay;
import quoi.module.impl.player.PlayerDisplay.HudType;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(Gui.class)
public class GuiMixin {

    @Redirect(
            method = "renderPlayerHealth",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getAbsorptionAmount()F"
            )
    )
    private float hideAbsorption(Player instance) {
        if (PlayerDisplay.shouldCancelHud(HudType.ABSORPTION)) {
            return 0.0F;
        }
        return instance.getAbsorptionAmount();
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Gui;renderSleepOverlay(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"
            )
    )
    private void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        new RenderEvent.Overlay(guiGraphics, deltaTracker).post();
    }

    @Inject(
            method = "renderArmor",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void cancelArmorBar(GuiGraphics context, Player player, int i, int j, int k, int x, CallbackInfo ci) {
        if (PlayerDisplay.shouldCancelHud(HudType.ARMOUR)) ci.cancel();
    }

    @Inject(
            method = "renderHearts",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cancelHealthBar(GuiGraphics context, Player player, int x, int y, int lines, int regeneratingHeartIndex, float maxHealth, int lastHealth, int health, int absorption, boolean blinking, CallbackInfo ci) {
        if (PlayerDisplay.shouldCancelHud(HudType.HEALTH)) ci.cancel();
    }

    @Inject(
            method = "renderFood",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cancelFoodBar(GuiGraphics context, Player player, int top, int right, CallbackInfo ci) {
        if (PlayerDisplay.shouldCancelHud(HudType.FOOD)) ci.cancel();
    }

    @Inject(
            method = "renderVehicleHealth",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cancelMountHealth(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (PlayerDisplay.shouldCancelHud(HudType.MOUNT_HEALTH)) ci.cancel();
    }

    @ModifyExpressionValue(
            method = "renderPlayerHealth",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;hasEffect(Lnet/minecraft/core/Holder;)Z"
            )
    )
    private boolean disableRegenBounce(boolean original) {
        return !PlayerDisplay.shouldCancelHud(HudType.REGEN_BOUNCE) && original;
    }

    @Unique private static final Pattern DATE_LINE_PATTERN = Pattern.compile("^(\\d{2}/\\d{2}/\\d{2}).*$");
    @Unique private static final Pattern STRIP_ALL_COLOR_PATTERN = Pattern.compile("(?i)§.");

    @Redirect(
            method = "displayScoreboardSidebar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/scores/Scoreboard;listPlayerScores(Lnet/minecraft/world/scores/Objective;)Ljava/util/Collection;"
            )
    )
    private Collection<PlayerScoreEntry> filterScores(Scoreboard scoreboard, Objective objective) {
        Collection<PlayerScoreEntry> originalList = scoreboard.listPlayerScores(objective);

        if (!ChatReplacements.getShouldHideServerId()) {
            return originalList;
        }

        List<PlayerScoreEntry> filteredList = new ArrayList<>();

        for (PlayerScoreEntry entry : originalList) {
            String ownerName = entry.owner();
            PlayerTeam team = scoreboard.getPlayersTeam(ownerName);

            Component baseComponent = entry.display() != null ? entry.display() : Component.literal(ownerName);
            Component formattedComponent = PlayerTeam.formatNameForTeam(team, baseComponent);
            String visibleText = formattedComponent.getString();

            String rawVisible = STRIP_ALL_COLOR_PATTERN.matcher(visibleText).replaceAll("").toLowerCase().trim();
            String rawOwner = STRIP_ALL_COLOR_PATTERN.matcher(ownerName).replaceAll("").toLowerCase().trim();

            if (rawVisible.contains("hypixel.net") || rawOwner.contains("hypixel.net")) {
                continue;
            }

            String cleanTextForDate = STRIP_ALL_COLOR_PATTERN.matcher(visibleText).replaceAll("").trim();
            Matcher matcher = DATE_LINE_PATTERN.matcher(cleanTextForDate);

            if (matcher.find()) {
                String dateOnly = matcher.group(1);
                String newText = "§7" + dateOnly;

                PlayerScoreEntry newEntry = new PlayerScoreEntry(
                        newText,
                        entry.value(),
                        entry.display(),
                        entry.numberFormatOverride()
                );

                filteredList.add(newEntry);
                continue;
            }

            filteredList.add(entry);
        }

        return filteredList;
    }
}
