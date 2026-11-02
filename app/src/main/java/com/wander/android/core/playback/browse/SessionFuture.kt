package com.wander.android.core.playback.browse

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

/**
 * Runs [block] in this scope and answers with its result as the [ListenableFuture] a Media3
 * session callback returns.
 *
 * `async` rather than `launch`: a failure stays in the future, where Media3 turns it into an
 * error result for the controller that asked, instead of reaching the scope's uncaught-exception
 * handler and taking the playback service down with it. Cancelling the future — a browser that
 * went away — cancels the work.
 */
internal fun <T> CoroutineScope.sessionFuture(block: suspend CoroutineScope.() -> T): ListenableFuture<T> {
    val result = SettableFuture.create<T>()
    val work = async(block = block)
    work.invokeOnCompletion { cause ->
        when (cause) {
            null -> result.set(work.getCompleted())
            is CancellationException -> result.cancel(false)
            else -> result.setException(cause)
        }
    }
    result.addListener({ if (result.isCancelled) work.cancel() }, MoreExecutors.directExecutor())
    return result
}
