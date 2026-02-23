package quoi.utils

import quoi.module.impl.misc.Test
import quoi.module.impl.dungeon.SecretTriggerBot
/**
 * Usage:
 * [Test.tickerExample]
 * [SecretTriggerBot.triggerBotTicker]
 */

sealed interface TickerStep {
    data class WaitTicks(val ticks: Int) : TickerStep
    data class WaitUntil(val condition: () -> Boolean) : TickerStep
    data class Action(val action: () -> Unit) : TickerStep
}

class Ticker(private val steps: ArrayDeque<TickerStep>) {
    private var currentWaitTicks = 0

    internal fun copy(): List<TickerStep> = steps.toList()

    fun tick(): Boolean {
        if (currentWaitTicks > 0) {
            currentWaitTicks--
            return false
        }

        while (steps.isNotEmpty()) {
            when (val step = steps.first()) {
                is TickerStep.WaitTicks -> {
                    steps.removeFirst()

                    currentWaitTicks = step.ticks
                    if (currentWaitTicks > 0) {
                        currentWaitTicks--
                        return false
                    }
                }
                is TickerStep.WaitUntil -> {
                    if (step.condition()) {
                        steps.removeFirst()
                        continue
                    }
                    return false
                }
                is TickerStep.Action -> {
                    step.action()
                    steps.removeFirst()
                    return false
                }
            }
        }
        return true
    }
}

class TickerScope {
    internal val steps = ArrayDeque<TickerStep>()

    fun delay(ticks: Int) {
        if (ticks > 0) steps.add(TickerStep.WaitTicks(ticks))
    }

    fun await(delayAfter: Int = 0, condition: () -> Boolean) {
        steps.add(TickerStep.WaitUntil(condition))
        delay(delayAfter)
    }

    fun action(ticks: Int = 0, block: () -> Unit) {
        delay(ticks)
        steps.add(TickerStep.Action(block))
    }

    fun addSteps(other: Ticker) {
        steps.addAll(other.copy())
    }

    fun scope(): Ticker = Ticker(steps)
}

fun ticker(block: TickerScope.() -> Unit): Ticker {
    return TickerScope().apply(block).scope()
}