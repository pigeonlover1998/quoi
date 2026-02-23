package quoi.api.commands.internal


import quoi.utils.ChatUtils.modMessage
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback

class BaseCommand(
    name: String,
    vararg aliases: String = emptyArray(),
    private val cb: (() -> Unit)? = null
) : CommandNode<BaseCommand>(name) {
    private val aliases = aliases.toList()

    override fun path() = name

    fun register() {
        addHelp()

        val node = literal(name)

        node.executes { ctx ->
            executeSafe(ctx) { cb?.invoke() ?: modMessage(buildHelpMessage()) }
        }

        subcommands.forEach { (name, overloads) ->
            val subLiteral = literal(name)
            overloads.forEach { it.attachTo(subLiteral) }
            node.then(subLiteral)
        }

//        node.then( // todo figure a way to handle this without the console spam..
//            ClientCommandManager.argument("invalid", StringArgumentType.greedyString())
//                .executes { ctx -> executeSafe(ctx) { modMessage(buildHelpMessage()) } }
//        )

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            val main = dispatcher.register(node)
            aliases.forEach { alias ->
                dispatcher.register(
                    literal(alias).executes { ctx ->
                        executeSafe(ctx) { cb?.invoke() ?: modMessage(buildHelpMessage()) }
                    }.redirect(main)
                )
            }
        }
    }
}