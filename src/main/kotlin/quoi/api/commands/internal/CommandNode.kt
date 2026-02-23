package quoi.api.commands.internal

import quoi.utils.ChatUtils.literal
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.trim
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style

@Suppress("UNCHECKED_CAST")
abstract class CommandNode<T : CommandNode<T>>(val name: String) {

    abstract fun path(): String

    protected var parent: CommandNode<*>? = null
    protected var description: String = "No description."

    val subcommands = mutableMapOf<String, MutableList<SubCommand>>()

    private var requirement: (FabricClientCommandSource) -> String? = { null }

    fun requires(message: String? = null, predicate: () -> Boolean): T {
        requirement = {
            if (predicate()) null
            else (message ?: "&cThis command is disabled!")
        }
        return this as T
    }

    fun description(desc: String): T {
        description = desc
        return this as T
    }

    fun sub(name: String, lambda: Function<*>): SubCommand {
        val sub = SubCommand(name.lowercase(), path(), lambda)
        sub.parent = this
        subcommands.getOrPut(sub.name) { ArrayList() }.add(sub)
        return sub
    }

    fun sub(name: String, lambda: () -> Unit = { modMessage(subcommands[name]!!.first().buildHelpMessage(), id = this.name.hashCode()) }) =
        sub(name, lambda as Function<*>)

    operator fun String.invoke(action: Function<*>): SubCommand = sub(this, action)
    operator fun String.invoke(action: () -> Unit): SubCommand = sub(this, action)

    private fun checkRequirement(source: FabricClientCommandSource): Boolean {
        if (parent?.checkRequirement(source) == false) return false

        val error = requirement(source)
        if (error != null) {
            modMessage(error)
            return false
        }
        return true
    }

    protected fun executeSafe(ctx: CommandContext<FabricClientCommandSource>, cb: () -> Unit): Int {
        if (!checkRequirement(ctx.source)) return 0
        return try {
            cb()
            1
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    fun buildHelpMessage(): MutableComponent {
        val message = literal("§rList of commands for §7$name§r:\n")

        subcommands.values.flatten()
            .sortedByDescending { it.name == "help" }
            .forEach { cmd ->
                val line = literal("  ")

                if (cmd.subcommands.isNotEmpty()) {
                    line.append("§7${cmd.name} ")

                    val hover = literal("")
                    cmd.subcommands.values.flatten().forEach {
                        if (it.name == "help") return@forEach
                        hover.append("§7- ${it.getUsage()}\n")
                    }

                    line.append(literal("§7<§a...§7>").withStyle(
                        Style.EMPTY.withHoverEvent(HoverEvent.ShowText(hover.trim()))
                    ))
                } else {
                    line.append(cmd.getUsage())
                }

                line.append(" §8:§r ${cmd.description}")

                line.withStyle(
                    Style.EMPTY.withClickEvent(ClickEvent.SuggestCommand(cmd.path()))
                )

                message.append(line).append("\n")
            }
        return message.trim()
    }

    fun addHelp() {
        if (!subcommands.containsKey("help")) {
            sub("help") { modMessage(buildHelpMessage(), id = name.hashCode()) }.description("Shows this list.")
        }
    }
}