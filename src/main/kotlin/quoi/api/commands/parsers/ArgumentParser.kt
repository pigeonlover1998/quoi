package quoi.api.commands.parsers

import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import java.lang.invoke.MethodHandles
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.ExperimentalReflectionOnLambdas
import kotlin.reflect.jvm.reflect

data class Argument(
    val name: String,
    val isOptional: Boolean,
    val type: Type
)

class ArgumentParser(lambda: Function<*>) {
    val arguments: List<Argument>
    private val executor: (Array<Any?>) -> Unit

    init {
        if (lambda is KFunction<*>) {
            this.arguments = lambda.parameters.map { it.toArgument() }
            this.executor = { args -> lambda.call(*args) }
        } else {
            @OptIn(ExperimentalReflectionOnLambdas::class)
            val reflected = lambda.reflect() ?: throw Exception("no good reflection")

            arguments = reflected.parameters.map { it.toArgument() }

            val method = lambda.javaClass.declaredMethods.find { it.name == "invoke" && !it.isBridge }
                ?: lambda.javaClass.declaredMethods.first()
            method.isAccessible = true
            val handle = MethodHandles.lookup().unreflect(method).bindTo(lambda)

            this.executor = { args -> handle.invokeWithArguments(*args) }
        }

        val firstOptional = arguments.indexOfFirst { it.isOptional }
        if (firstOptional != -1 && arguments.drop(firstOptional).any { !it.isOptional }) {
            throw IllegalArgumentException("reqd args can't follow optional args in .args() declaration")
        }
    }

    fun execute(parsedArgs: Array<Any?>) {
        executor(parsedArgs)
    }

    private fun KParameter.toArgument() = Argument(
        name = this.name ?: "arg${this.index}", // should never happen
        isOptional = this.type.isMarkedNullable,
        type = (this.type.classifier as KClass<*>).java
    )
}

fun CommandContext<FabricClientCommandSource>.arg(index: Int): String? =
    input.trim().split(" ").drop(2).getOrNull(index)