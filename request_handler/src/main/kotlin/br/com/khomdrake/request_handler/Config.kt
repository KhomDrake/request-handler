package br.com.khomdrake.request_handler

import androidx.annotation.WorkerThread
import br.com.khomdrake.request_handler.cache.Cache
import br.com.khomdrake.request_handler.data.Response
import br.com.khomdrake.request_handler.data.responseData
import br.com.khomdrake.request_handler.data.responseError
import br.com.khomdrake.request_handler.data.responseLoading
import br.com.khomdrake.request_handler.exception.RequestNotImplementedException
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

enum class CacheType {
    MEMORY,
    DISK
}

class Config<Data>(private val key: String) {
    private var maxDuration = 5.minutes
    private var minDuration: Duration = 200.milliseconds
    private val withCache: Boolean
        get() = cache != null
    private var execution: (suspend () -> Data)? = null
    private var cache: Cache<Data>? = null

    fun maxDuration(duration: Duration) = apply {
        maxDuration = duration
    }

    fun minDuration(duration: Duration) = apply {
        minDuration = duration
    }

    fun cache(function: Cache<Data>.() -> Unit) = apply {
        cache = Cache<Data>(key)
            .apply(function)
    }

    fun request(function: suspend () -> Data) = apply {
        execution = function
    }

    @WorkerThread
    suspend fun execute(
        collector: FlowCollector<Response<Data>>,
        handler: RequestHandler<Data>
    ) {
        withTimeout(maxDuration) {
            runCatching {
                val currentTime = System.currentTimeMillis()
                val cacheExpired = cache?.isExpired(currentTime) ?: true
                collector.emit(responseLoading())

                if(!withCache) {
                    executionWithoutCache(handler, collector)
                } else if(cacheExpired && withCache) {
                    executionCacheExpiredOrNotSet(handler, collector, isExpired = true)
                } else {
                    executionCache(handler, collector)
                }
            }.onFailure {
                handler.logInfo("[Config] Emitting error")
                collector.emit(responseError(it))
                handler.logInfo("[Config] Error emitted: $it")
            }
        }
    }

    private suspend inline fun executionCache(
        handler: RequestHandler<Data>,
        collector: FlowCollector<Response<Data>>
    ) {
        handler.logInfo("[Config] using cache key: $key")
        val savedData = cache?.retrieve()
        savedData?.let {
            handler.logInfo("[Config] cache retrieved: $it")
            collector.emit(responseData(savedData))
        } ?: executionCacheExpiredOrNotSet(handler, collector, isExpired = false)
    }

    private suspend inline fun executionCacheExpiredOrNotSet(
        handler: RequestHandler<Data>,
        collector: FlowCollector<Response<Data>>,
        isExpired: Boolean
    ) {
        if(isExpired) {
            handler.logInfo("[Config] Request started - Cache expired")
            cache?.remove()
        } else handler.logInfo("[Config] Request started - Cache not set")

        val execution = execution ?: throw RequestNotImplementedException()
        val data = execution.invoke()
        handler.logInfo("[Config] Request ended, data: $data")
        cache?.save(data)
        handler.logInfo("[Config] Data emitted: $data")
        collector.emit(responseData(data))
    }

    private suspend inline fun executionWithoutCache(
        handler: RequestHandler<Data>,
        collector: FlowCollector<Response<Data>>
    ) {
        handler.logInfo("[Config] Request started")
        val execution = execution ?: throw RequestNotImplementedException()
        val data = execution.invoke()
        handler.logInfo("[Config] Request ended, data: $data")
        collector.emit(responseData(data))
        handler.logInfo("[Config] Data emitted: $data")
    }

}