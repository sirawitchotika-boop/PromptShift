package com.promptshift.app.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Serialized single-threaded snapshot writer for repository disk persistence.
 *
 * - When [dispatcher] is provided (production: `Dispatchers.IO`), snapshots are sent to a
 *   CONFLATED channel and written sequentially on a single background thread (`limitedParallelism(1)`),
 *   preventing Main-thread disk I/O stalls and `.tmp` file rename races while always persisting the
 *   latest state.
 * - When [dispatcher] is null (unit tests), writes run synchronously inline for deterministic assertions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class AsyncSnapshotWriter<T>(
    dispatcher: CoroutineDispatcher?,
    private val writeAction: (T) -> Unit,
) {
    private val channel: Channel<T>? = if (dispatcher != null) {
        Channel(Channel.CONFLATED)
    } else {
        null
    }

    init {
        if (dispatcher != null && channel != null) {
            val singleThreadScope = CoroutineScope(SupervisorJob() + dispatcher.limitedParallelism(1))
            singleThreadScope.launch {
                for (snapshot in channel) {
                    writeAction(snapshot)
                }
            }
        }
    }

    fun enqueue(snapshot: T) {
        val ch = channel
        if (ch != null) {
            ch.trySend(snapshot)
        } else {
            writeAction(snapshot)
        }
    }
}
