package br.com.khomdrake.request_handler

import androidx.annotation.WorkerThread
import br.com.khomdrake.request_handler.cache.Cache
import br.com.khomdrake.request_handler.data.Response
import br.com.khomdrake.request_handler.data.responseData
import br.com.khomdrake.request_handler.data.responseError
import br.com.khomdrake.request_handler.data.responseLoading
import br.com.khomdrake.request_handler.exception.RequestNotImplementedException
import br.com.khomdrake.request_handler.log.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.withContext
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
    private var execution: (suspend CoroutineScope.() -> Data)? = null
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

    fun request(function: suspend CoroutineScope.() -> Data) = apply {
        execution = function
    }

    @WorkerThread
    suspend fun executeData(handler: RequestHandler<Data>) = kotlin.run {
        val startTime = System.currentTimeMillis()
        withTimeoutLog(
            handler,
            startTime,
            maxDuration
        ) {
            val cacheExpired = cache?.isExpired(startTime) ?: true

            return@withTimeoutLog if(!withCache) {
                executionWithoutCache(handler, startTime)
            } else if (cacheExpired && withCache) {
                executionCacheExpiredOrNotSet(handler, isExpired = true, startTime)
            } else {
                executionCache(handler, startTime)
            }
        }
    }


    private suspend fun <T> withTimeoutLog(
        handler: RequestHandler<Data>,
        startTime: Long,
        timeout: Duration,
        block: suspend CoroutineScope.() -> T
    ) = withTimeout(
        timeout
    ) {
        val data = block.invoke(this)
        val endTime = System.currentTimeMillis()
        handler.logInfo(
            area = "Config",
            message = "Time to finish: ${endTime - startTime}",
            level = LogLevel.TWO
        )
        data
    }

    @WorkerThread
    suspend fun execute(
        collector: FlowCollector<Response<Data>>,
        handler: RequestHandler<Data>
    ) {
        val startTime = System.currentTimeMillis()
        withTimeoutLog(handler, startTime, maxDuration) {
            runCatching {
                val currentTime = System.currentTimeMillis()
                val cacheExpired = cache?.isExpired(currentTime) ?: true
                collector.emit(responseLoading())

                if(!withCache) {
                    executionWithoutCache(handler, collector, startTime)
                } else if(cacheExpired && withCache) {
                    executionCacheExpiredOrNotSet(handler, collector, isExpired = true, startTime)
                } else {
                    executionCache(handler, collector, startTime)
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
        collector: FlowCollector<Response<Data>>,
        startTime: Long
    ) {
        handler.logInfo(
            area = "Config",
            message = "Using Cache Key: $key",
            level = LogLevel.ONE
        )
        val dataCached = cache?.retrieve()
        handler.logInfo(
            area = "Config",
            message = "cache retrieved: $dataCached",
            level = LogLevel.ONE
        )
        return dataCached?.let {
            handleMinDuration(startTime, handler)
            handler.logInfo(
                area = "Config",
                message = "Emitting cache: $dataCached",
                level = LogLevel.ONE
            )
            collector.emit(responseData(dataCached))
        } ?: executionCacheExpiredOrNotSet(handler, collector, isExpired = false, startTime = startTime)
    }

    private suspend inline fun executionCacheExpiredOrNotSet(
        handler: RequestHandler<Data>,
        collector: FlowCollector<Response<Data>>,
        isExpired: Boolean,
        startTime: Long
    ) {
        if(isExpired) {
            handler.logInfo(
                area = "Config",
                message = "Request Start - Cache Expired",
                level = LogLevel.ONE
            )
            cache?.remove()
        } else handler.logInfo(
            area = "Config",
            message = "Request started - Cache not set",
            level = LogLevel.ONE
        )

        val execution = execution ?: throw RequestNotImplementedException()
        val data = withContext(handler.scope.coroutineContext) {
            execution.invoke(this)
        }
        handler.logInfo(
            area = "Config",
            message = "Request ended, data: $data",
            level = LogLevel.ONE
        )
        cache?.save(data)
        handleMinDuration(startTime, handler)
        handler.logInfo(
            area = "Config",
            message = "Data emitted: $data",
            level = LogLevel.ONE
        )
        collector.emit(responseData(data))
    }

    private suspend inline fun executionWithoutCache(
        handler: RequestHandler<Data>,
        collector: FlowCollector<Response<Data>>,
        startTime: Long
    ) {
        handler.logInfo(
            area = "Config",
            message = "Request started - No Cache",
            level = LogLevel.ONE
        )
        val execution = execution ?: throw RequestNotImplementedException()
        val data = withContext(handler.scope.coroutineContext) {
            execution.invoke(this)
        }
        handler.logInfo(
            area = "Config",
            message = "Request ended, data: $data",
            level = LogLevel.ONE
        )
        handleMinDuration(startTime, handler)
        collector.emit(responseData(data))
        handler.logInfo(
            area = "Config",
            message = "Data emitted: $data",
            level = LogLevel.ONE
        )
    }

    private suspend inline fun executionCache(
        handler: RequestHandler<Data>,
        startTime: Long
    ) : Data {
        handler.logInfo(
            area = "Config",
            message = "Using Cache Key: $key",
            level = LogLevel.ONE
        )
        val dataCached = cache?.retrieve()
        handler.logInfo(
            area = "Config",
            message = "cache retrieved: $dataCached",
            level = LogLevel.ONE
        )

        return dataCached?.also {
            handleMinDuration(startTime, handler)
            handler.logInfo(
                area = "Config",
                message = "Emitting cache: $dataCached",
                level = LogLevel.ONE
            )
        } ?: executionCacheExpiredOrNotSet(handler, isExpired = false, startTime)
    }

    private suspend inline fun executionCacheExpiredOrNotSet(
        handler: RequestHandler<Data>,
        isExpired: Boolean,
        startTime: Long
    ): Data {
        if(isExpired) {
            handler.logInfo(
                area = "Config",
                message = "Request Start - Cache Expired",
                level = LogLevel.ONE
            )
            cache?.remove()
        } else handler.logInfo(
            area = "Config",
            message = "Request started - Cache not set",
            level = LogLevel.ONE
        )

        val execution = execution ?: throw RequestNotImplementedException()
        val data = withContext(handler.scope.coroutineContext) {
            execution.invoke(this)
        }
        handler.logInfo(
            area = "Config",
            message = "Request ended, data: $data",
            level = LogLevel.ONE
        )
        cache?.save(data)

        handleMinDuration(startTime, handler)

        handler.logInfo(
            area = "Config",
            message = "Data emitted: $data",
            level = LogLevel.ONE
        )
        return data
    }

    private suspend inline fun executionWithoutCache(
        handler: RequestHandler<Data>,
        startTime: Long
    ): Data {
        handler.logInfo(
            area = "Config",
            message = "Request started - No Cache",
            level = LogLevel.ONE
        )
        val execution = execution ?: throw RequestNotImplementedException()
        val data = withContext(handler.scope.coroutineContext) {
            execution.invoke(this)
        }
        handler.logInfo(
            area = "Config",
            message = "Request ended, data: $data",
            level = LogLevel.ONE
        )

        handleMinDuration(startTime, handler)

        handler.logInfo(
            area = "Config",
            message = "Data emitted: $data",
            level = LogLevel.ONE
        )
        return data
    }

    private suspend fun handleMinDuration(startTime: Long, handler: RequestHandler<Data>) {
        val operationDuration = System.currentTimeMillis() - startTime
        val delta = minDuration.inWholeMilliseconds - operationDuration

        when {
            delta > 0 -> {
                handler.logInfo(
                    area = "Config",
                    message = "Execution time was ${operationDuration}ms, need to wait more ${delta}ms",
                    level = LogLevel.ONE
                )
                delay(delta)
            }
            else -> Unit
        }
    }

}