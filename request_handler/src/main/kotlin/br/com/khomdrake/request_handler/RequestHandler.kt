package br.com.khomdrake.request_handler

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import br.com.khomdrake.request_handler.cache.DiskVault
import br.com.khomdrake.request_handler.data.Response
import br.com.khomdrake.request.data.flow.MutableResponseStateFlow
import br.com.khomdrake.request_handler.cache.MemoryVault
import br.com.khomdrake.request_handler.data.flow.ResponseStateFlow
import br.com.khomdrake.request_handler.data.responseData
import br.com.khomdrake.request_handler.data.responseError
import br.com.khomdrake.request_handler.data.responseLoading
import br.com.khomdrake.request_handler.log.LogHandler
import br.com.khomdrake.request_handler.log.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class RequestHandler<Data>(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val key: String = ""
) {

    private val lock = object {}

    private var config: Config<Data> = Config(key)

    private val _responseStateFlow = MutableResponseStateFlow<Data>(Response())

    val responseStateFlow: ResponseStateFlow<Data>
        get() = _responseStateFlow
            .shareIn(
                requestScope,
                SharingStarted.WhileSubscribed(),
                replay = 0
            )

    fun config(func: Config<Data>.() -> Unit) = apply {
        config = Config<Data>(key).apply(func)
    }

    fun logInfo(message: String, level: LogLevel = LogLevel.ZERO) {
        LogHandler.d(topic = "Request Handler: $key", message = message.take(200), level = level)
    }

    fun logInfo(message: String, area: String, level: LogLevel = LogLevel.ZERO) {
        LogHandler.d(topic = "Request Handler: $key", message = "($area) ${message.take(200)}", level = level)
    }

    @WorkerThread
    suspend fun executeData() = run {
        logInfo("Request Handler - Execute Data")
        config.executeData(this)
    }

    fun execute() = synchronized(lock) {
        scope.launch {
            runCatching {
                createExecution().start()
            }.onFailure {
                _responseStateFlow.emitError(it)
            }
        }
        return@synchronized this
    }

    @VisibleForTesting
    fun setData(data: Data) = apply {
        requestScope.launch {
            _responseStateFlow.emit(responseData(data))
        }
    }

    @VisibleForTesting
    fun setLoading() {
        requestScope.launch {
            _responseStateFlow.emit(responseLoading())
        }
    }

    @VisibleForTesting
    fun setError(throwable: Throwable = Throwable()) {
        requestScope.launch {
            _responseStateFlow.emit(responseError(throwable))
        }
    }

    private fun createExecution() = scope.launch {
        logInfo("[Execution] creating execution")
        flow {
            config.execute(
                this,
                this@RequestHandler
            )
        }
            .catch {
                _responseStateFlow.emitError(it)
            }
            .collect {
                _responseStateFlow.emit(it)
            }

    }

    companion object {

        fun init(context: Context, cleanCacheTimeout: Boolean = false) {
            DiskVault.init(context)
            if(cleanCacheTimeout) DiskVault.clearCache()
        }

        fun clear() {
            DiskVault.clearCache()
            MemoryVault.clearCache()
        }

    }

}

fun <T> requestHandler(
    tag: String = "",
    scope: CoroutineScope = requestScope
) : RequestHandler<T> {
    return RequestHandler(scope, tag)
}

fun <T> requestHandler(
    tag: String = "",
    scope: CoroutineScope = requestScope,
    func: Config<T>.() -> Unit
) : RequestHandler<T> {
    return RequestHandler<T>(scope, tag).config(func)
}
