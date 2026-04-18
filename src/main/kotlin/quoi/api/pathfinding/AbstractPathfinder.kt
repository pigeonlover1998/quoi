package quoi.api.pathfinding

import quoi.api.pathfinding.context.PathContext
import kotlin.concurrent.thread

abstract class AbstractPathfinder<N : PathNode, T : PathContext<N>> {

    abstract fun expand(ctx: T, current: N)

    open fun isGoal(ctx: T, current: N) = current.pos == ctx.goal

    @Suppress("UNCHECKED_CAST")
    open fun reconstructPath(node: N): List<N> {
        val path = mutableListOf<N>()
        var current: PathNode? = node
        while (current != null) {
            path.add(current as N)
            current = current.parent
        }
        return path.reversed()
    }

    fun find(ctx: T, threadCount: Int): List<N>? {
        val threads = List(threadCount - 1) {
            thread { runWorker(ctx) }
        }

        runWorker(ctx)

        threads.forEach {
            it.join(100)
        }

        return ctx.finalPath
    }

    private fun runWorker(ctx: T) {
        while (!ctx.solved) {
            if (System.currentTimeMillis() > ctx.endTime) {
                ctx.timedOut = true
                ctx.solved = true
                return
            }

            val current = ctx.getNext()

            if (current == null) {
                if (ctx.isDone) ctx.solved = true
                else Thread.yield()
                continue
            }

            ctx.processed.incrementAndGet()

            if (isGoal(ctx, current)) {
                if (!ctx.solved) {
                    ctx.finalPath = reconstructPath(current)
                    ctx.solved = true
                }
                return
            }

            expand(ctx, current)
            ctx.finishNode(current.pos.asLong())
        }
    }
}