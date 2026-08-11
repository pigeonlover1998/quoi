package quoi.api.events.core

import kotlinx.coroutines.*
import quoi.QuoiMod.mc
import quoi.api.events.TickEvent
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.*

class ListenerInactiveException(listener: EventListener) :
    CancellationException("listener ${listener::class.simpleName} is no longer active")

/**
 * prevents suspended code from executing after [EventListener.running] is set to false
 */
private class ActiveStateInterceptor(
    private val cont: ContinuationInterceptor?,
    private val listener: EventListener,
) : AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {

    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {

        val cont = cont?.interceptContinuation(continuation) ?: continuation

        return object : Continuation<T> {

            override val context get() = continuation.context

            override fun resumeWith(result: Result<T>) {
                val res = if (listener.running) result
                else Result.failure(ListenerInactiveException(listener))
                cont.resumeWith(res)
            }
        }
    }
}

private val asyncScopes = ConcurrentHashMap<EventListener, CoroutineScope>()

val EventListener.asyncScope: CoroutineScope
    get() = asyncScopes.compute(this) { listener, scope ->
        if (scope != null && scope.coroutineContext[Job]?.isActive == true) {
            return@compute scope
        }

        val parentJob = listener.parent()?.asyncScope?.coroutineContext?.get(Job)
        val job = if (parentJob != null) SupervisorJob(parentJob) else SupervisorJob()

        job.invokeOnCompletion {
            asyncScopes.remove(listener)
        }

        CoroutineScope(
            job +
            CoroutineExceptionHandler { _, t -> if (t !is ListenerInactiveException) t.printStackTrace() } +
            ActiveStateInterceptor(mc.asCoroutineDispatcher(), listener)
        )
    }!!

/**
 * cancels all running async tasks for this [EventListener]
 */
fun EventListener.removeAsyncScope() {
    asyncScopes.remove(this)?.cancel(ListenerInactiveException(this))
}


/**
 * asynccronouseaelye subscribes to [Event]
 * if the job is active, the new event dispatch is discarded
 */
inline fun <reified T : Event> EventListener.onAsync(
    priority: Int = 0,
    acceptCancelled: Boolean = false,
    crossinline block: suspend CoroutineScope.(T) -> Unit
): Subscription<T> {
    var job: Job? = null

    return on<T>(priority, acceptCancelled) {
        if (job?.isActive == true) return@on // maybe make this part customisable

        job = asyncScope.launch {
            block(this@on)
        }
    }
}

/**
 * Suspends execution for [ticks] amount
 */
suspend fun EventListener.wait(ticks: Int) {
    if (ticks <= 0) return
    var remaining = ticks

    suspendCancellableCoroutine { cont ->
        val subscription = until<TickEvent.Start> {
            remaining--
            if (remaining <= 0) {
                if (cont.isActive) cont.resume(Unit)
                true
            } else {
                false
            }
        }

        cont.invokeOnCancellation { subscription.unregister() }
    }
}