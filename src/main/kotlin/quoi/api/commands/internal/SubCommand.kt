package quoi.api.commands.internal

import quoi.api.commands.parsers.ArgumentParser
import quoi.api.commands.parsers.TypeParser
import quoi.utils.ChatUtils.modMessage
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.commands.SharedSuggestionProvider
import java.lang.reflect.Type

class SubCommand(
    name: String,
    private val path: String,
    lambda: Function<*>
) : CommandNode<SubCommand>(name) {

    private val staticSuggestions = mutableMapOf<String, () -> List<String>>()
    private val ctxSuggestions = mutableMapOf<String, (CommandContext<FabricClientCommandSource>) -> List<String>>()

    private val parser: ArgumentParser = ArgumentParser(lambda)

    override fun path() = "/$path $name"

    fun suggests(argName: String, supplier: () -> Any): SubCommand {
        val key = if (argName.isEmpty()) {
            check(parser.arguments.size == 1) { "single arg .suggests() can only be used on subcommands with one arg" }
            parser.arguments[0].name.lowercase()
        } else argName.lowercase()

        staticSuggestions[key] = {
            when (val s = supplier()) {
                is Iterable<*> -> s.map { it.toString() }
                is Array<*> -> s.map { it.toString() }
                else -> listOf(s.toString())
            }
        }

        return this
    }

    fun suggests(argName: String, values: List<*>) = suggests(argName) { values }
    fun suggests(argName: String, vararg values: Any) = suggests(argName) { values.toList() }

    fun suggests(supplier: () -> Any) = suggests("", supplier)
    fun suggests(values: List<*>) = suggests("") { values }
    fun suggests(vararg values: Any) = suggests("") { values.toList() }

    fun suggestsCtx(
        argName: String,
        supplier: (CommandContext<FabricClientCommandSource>) -> List<String>
    ): SubCommand {
        ctxSuggestions[argName.lowercase()] = supplier
        return this
    }

    fun attachTo(node: LiteralArgumentBuilder<FabricClientCommandSource>) {
        if (subcommands.isNotEmpty()) addHelp()

        subcommands.forEach { (name, overloads) ->
            val subLiteral =  literal(name)
            overloads.forEach { it.attachTo(subLiteral) }
            node.then(subLiteral)
        }

        if (parser.arguments.isEmpty()) {
            node.executes { executeSafe(it) { parser.execute(emptyArray()) } }
        } else if (parser.arguments.first().isOptional) {
            node.executes { executeSafe(it) { parser.execute(arrayOfNulls(parser.arguments.size)) } }
        } else if (node.command == null) {
            node.executes { executeSafe(it) { modMessage("Usage: &7/$path ${getUsage()}", id = name.hashCode() + 1) } }
        }

        if (parser.arguments.isEmpty()) return

        val argumentNodes = parser.arguments.map { arg ->
            ClientCommandManager.argument(arg.name, TypeParser.getBrigadierType(arg.type)).apply {
                applySuggestions(this, arg.type)
            }
        }

        argumentNodes.indices.reversed().forEach { i ->
            val node = argumentNodes[i]
            val isLast = i == argumentNodes.lastIndex
            val nextIsOptional = if (!isLast) parser.arguments[i + 1].isOptional else false

            if (isLast || nextIsOptional) {
                node.executes { ctx ->
                    executeSafe(ctx) {
                        val args = parser.arguments.mapIndexed { index, arg ->
                            if (index <= i) TypeParser.getValue(ctx, arg.name, arg.type)
                            else null
                        }
                        parser.execute(args.toTypedArray())
                    }
                }
            } else {
                node.executes { executeSafe(it) { modMessage(buildHelpMessage(), id = name.hashCode()) } }
            }

            if (!isLast) node.then(argumentNodes[i + 1])
        }

        node.then(argumentNodes.first())
    }

    private fun applySuggestions(node: RequiredArgumentBuilder<FabricClientCommandSource, *>, type: Type) {
        val key = node.name.lowercase()

        staticSuggestions[key]?.let { suggestions ->
            node.suggests { _, builder -> SharedSuggestionProvider.suggest(suggestions(), builder) }
        }

        ctxSuggestions[key]?.let { suggestions ->
            node.suggests { ctx, builder ->
                val list = suggestions(ctx)
                if (type == GreedyString::class.java) {
                    val lastSpace = builder.remaining.lastIndexOf(' ')
                    val offsetBuilder = if (lastSpace != -1) builder.createOffset(builder.start + lastSpace + 1) else builder
                    SharedSuggestionProvider.suggest(list, offsetBuilder)
                } else {
                    SharedSuggestionProvider.suggest(list, builder)
                }
            }
        }
    }

    fun getUsage(): String {
        val argsUsage = parser.arguments.joinToString(" ") {
            if (it.isOptional) "§7[§b${it.name}§7]" else "§7<§b${it.name}§7>"
        }
        return "§7$name $argsUsage".trim()
    }
}