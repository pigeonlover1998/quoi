package quoi.api.commands.parsers

import quoi.api.commands.internal.GreedyString
import com.mojang.brigadier.arguments.*
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import java.lang.reflect.Type

object TypeParser {
fun getBrigadierType(type: Type): ArgumentType<*> {
    return when (type) {
        Int::class.java, Integer::class.java -> IntegerArgumentType.integer()
        Double::class.java, java.lang.Double::class.java -> DoubleArgumentType.doubleArg()
        Boolean::class.java, java.lang.Boolean::class.java -> BoolArgumentType.bool()
        String::class.java -> StringArgumentType.string()
        GreedyString::class.java -> StringArgumentType.greedyString()
        else -> throw Exception("no good type $type")
    }
}

    fun getValue(context: CommandContext<FabricClientCommandSource>, name: String, type: Type): Any {
        return when (type) {
            Int::class.java, Integer::class.java -> IntegerArgumentType.getInteger(context, name)
            Double::class.java, java.lang.Double::class.java -> DoubleArgumentType.getDouble(context, name)
            Boolean::class.java, java.lang.Boolean::class.java -> BoolArgumentType.getBool(context, name)
            String::class.java -> StringArgumentType.getString(context, name)
            GreedyString::class.java -> GreedyString(StringArgumentType.getString(context, name))
            else -> throw Exception("no good type $type")
        }
    }
}