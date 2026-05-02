package quoi.module.impl.player

import dev.kord.core.Kord
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.reply
import dev.kord.core.entity.Message
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.message.addFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.minecraft.Util
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import quoi.QuoiMod.scope
import quoi.api.events.GameEvent
import quoi.api.events.ServerEvent
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.ChatUtils
import quoi.utils.ChatUtils.literal
import quoi.utils.ChatUtils.modMessage
import quoi.utils.clickCount
import quoi.utils.skyblock.player.MovementUtils.hold
import java.net.URI
import java.nio.file.Files
import kotlin.coroutines.cancellation.CancellationException

object RemoteControl : Module(
    "Remote control",
    desc = "Controls the game through discord bot."
) {
    private val botToken by textInput("Bot token", length = 0).censors()
    private val prefix by textInput("Prefix", "!")

    private val commandsDropdown by text("Commands")

    private val login by button("Login") {
        if (!enabled) return@button
        if (botToken.isEmpty()) return@button modMessage("Bot token is empty.")
        if (job?.isActive == true) return@button modMessage("Bot is already running.")
        start()
    }

    private val guide by button("How to setup") {
        Util.getPlatform().openUri(URI("https://github.com/kordlib/kord/wiki/Getting-Started#creating-a-bot-application"))
    }

    private var kord: Kord? = null
    private var job: Job? = null
    private val commands = mutableMapOf<String, DiscordCommand>()
    private var pending: Message? = null

    init {
        cmd("help", "Shows list of commands") {
            val text = buildString {
                append("**Commands:**\n")
                commands.values.forEach { command ->
                    val desc = if (command.desc.isNotEmpty()) "- ${command.desc} " else ""
                    val toggled = if (!command.enabled.value) "*(disabled)*" else ""

                    append("`$prefix${command.name}` $desc$toggled\n")
                }
            }
            reply(text)
        }

//        cmd("ping", "Replies with pong") {
//            reply("pong")
//        }

        cmd("screenshot", "Takes a screenshot of the game") {
            mc.execute {
                Screenshot.takeScreenshot(mc.mainRenderTarget) { image ->
                    Util.ioPool().execute {
                        val temp = Files.createTempFile("screenshot", ".png")
                        image.writeToFile(temp)
                        image.close()

                        scope.launch {
                            message.reply { addFile(temp) }
                            Files.deleteIfExists(temp)
                        }
                    }
                }
            }
        }

        cmd("key", "Presses a specified key by its internal name (attack, close, menu, etc)") { args ->
            if (mc.level == null) return@cmd reply("You're not on a server")
            if (args.isEmpty()) return@cmd reply("Provide a key")

            val key = args[0].lowercase()

            mc.execute {

                if (key == "menu") {
                    mc.setScreen(null)
                    mc.pauseGame(false)
                    scope.launch { reply("Opened menu") }
                    return@execute
                }

                if (key == "close") {
                    mc.setScreen(null)
                    scope.launch { reply("Closed container") }
                    return@execute
                }

                val keyMapping = mc.options.keyMappings.find { it.name.contains(key, ignoreCase = true) }

                if (keyMapping != null) {
                    keyMapping.clickCount++
                    keyMapping.hold(1)
                    scope.launch { reply("Pressed `${keyMapping.name}`") }
                } else {
                    scope.launch { reply("Unknown key `$key`") }
                }
            }
        }

        cmd("run", "Executes a specified command") { args ->
            if (args.isEmpty()) return@cmd reply("Provide a command")
            val command = args.joinToString(" ")
            reply("Executed `$command`")
            mc.execute {
                ChatUtils.command(command)
            }
        }

        cmd("connect", "Connects to a specified server") { args ->
            if (args.isEmpty()) return@cmd reply("Provide an ip")

            val ip = args[0]

            pending = reply("Connecting to `$ip`...")

            mc.execute {
                mc.level?.disconnect(literal("rce connect"))
                val address = ServerAddress.parseString(ip)
                val serverData = ServerData("rce", ip, ServerData.Type.OTHER)
                ConnectScreen.startConnecting(TitleScreen(), mc, address, serverData, false, null)
            }
        }

        cmd("reconnect", "Reconnects to the current server") {
            val serverData = mc.currentServer ?: return@cmd reply("You are not on the server")

            mc.execute { mc.level?.disconnect(literal("rce reconnect")) }

            val ip = serverData.ip
            pending = reply("Reconnecting to `$ip`...")

            mc.execute {
                val address = ServerAddress.parseString(ip)
                ConnectScreen.startConnecting(TitleScreen(), mc, address, serverData, false, null)
            }
        }

        cmd("disconnect", "Disconnects from the current server") {
            if (mc.level == null) return@cmd reply("You're not on a serevre")

            pending = reply("Disconnecting")

            mc.execute {
                mc.level?.disconnect(literal("rce disconnect"))
            }
        }

        on<ServerEvent.Connect> {
            pending?.let {
                scope.launch {
                    it.edit {
                        content = it.content
                            .replace("onnecting", "onnected")
                            .replace("...", "")
                    }
                }
                pending = null
            }
        }

        on<ServerEvent.Disconnect> {
            pending?.let {
                scope.launch { it.edit { content = "Disconnected" } }
                pending = null
            }
        }

//        on<GameEvent.Load> { // todo figure why it doesn't work here..
//            start()
//        }

        on<GameEvent.Unload> {
            stop()
        }
    }

    override fun onEnable() {
        super.onEnable()
        start()
    }

    override fun onDisable() {
        super.onDisable()
        stop()
    }

    private suspend fun MessageCreateEvent.handle() {
        if (message.author?.isBot == true) return
        val content = message.content

        if (!content.startsWith(prefix)) return

        val parts = content.removePrefix(prefix).trim().split(" ")
        if (parts.isEmpty() || parts[0].isEmpty()) return

        val cmd = parts[0].lowercase()
        val args = parts.drop(1)

        val command = commands[cmd]
        if (command != null) {
            if (command.enabled.value) {
                try {
                    command.block(this, args)
                } catch (e: Exception) {
                    reply("error executing command: ```${e.message}```")
                    e.printStackTrace()
                }
            } else {
                reply("`$cmd` is disabled")
            }
        }
    }

    fun start() {
        if (!enabled) return
        if (botToken.isEmpty()) return
        job = scope.launch {
            try {
                val client = Kord(botToken)
                kord = client

                client.on<MessageCreateEvent> {
                    handle()
                }

                client.login {
                    @OptIn(PrivilegedIntent::class)
                    intents += Intent.MessageContent
                }
            } catch (e: Exception) {
                if (e is CancellationException) return@launch

                modMessage(
                    ChatUtils.button(
                        "&cError occurred &7(click to copy)",
                        command = "/quoidev copy ${e.stackTraceToString()}",
                        hoverText = "Click to copy"
                    )
                )
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        runBlocking {
            kord?.logout()
            job?.cancel()
        }
        kord = null
        job = null
    }

    private fun cmd(name: String, desc: String = "", block: suspend MessageCreateEvent.(List<String>) -> Any) {
        commands[name.lowercase()] = DiscordCommand(name, desc, block)
    }

    private suspend fun MessageCreateEvent.reply(text: String) = message.reply {
        content = text
    }

    data class DiscordCommand(
        val name: String,
        val desc: String,
        val block: suspend MessageCreateEvent.(List<String>) -> Any
    ) {
        val enabled = +switch(name, true, desc = desc).childOf(::commandsDropdown)
    }
}