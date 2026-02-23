package quoi

import quoi.api.commands.QuoiCommand
import quoi.api.events.GameEvent
import quoi.api.events.core.EventBus
import quoi.api.skyblock.Location
import quoi.api.skyblock.SkyblockPlayer
import quoi.api.skyblock.dungeon.Dungeon
import quoi.config.Config
import quoi.module.ModuleManager
import quoi.utils.EntityUtils
import quoi.utils.skyblock.PartyUtils
import quoi.utils.skyblock.SplitsManager
import quoi.utils.skyblock.player.AuraManager
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.ui.hud.HudManager
import quoi.utils.ui.rendering.NVGSpecialRenderer
import kotlinx.coroutines.CoroutineScope
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import kotlin.coroutines.EmptyCoroutineContext

object QuoiMod : ClientModInitializer {

    const val MOD_ID = "quoi"
    val mc: Minecraft get() = Minecraft.getInstance()
    val scope = CoroutineScope(EmptyCoroutineContext)
    val logger: Logger = LogManager.getLogger("quoi")

    override fun onInitializeClient() {
        ModuleManager.initialise()
        Location.init()
        SkyblockPlayer.init()
        SwapManager.init()
        AuraManager.init()

        SpecialGuiElementRegistry.register { context ->
            NVGSpecialRenderer(context.vertexConsumers())
        }

        var schizophrenia: EventBus.EventListener? = null
        schizophrenia = EventBus.on<GameEvent.Load> {
            HudManager.init()
            schizophrenia?.remove()
        }
        Dungeon.init()
        PartyUtils.init()
        PlayerUtils.init()
        EntityUtils.init()
        SplitsManager.init()

        QuoiCommand.initialise()
        Config.load()
    }
}