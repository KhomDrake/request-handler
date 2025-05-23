package br.com.khomdrake.request_handler.data.flow

import br.com.khomdrake.request_handler.data.Response
import br.com.khomdrake.request.data.flow.MutableResponseStateFlow
import br.com.khomdrake.request_handler.extension.emitError
import br.com.khomdrake.request_handler.extension.mapData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

open class ResponseStateFlow<Data>(
    previousData: Response<Data> = Response(),
    newScope: CoroutineScope
) : StateFlow<Response<Data>> {

    internal val flowState: MutableStateFlow<Response<Data>> = MutableStateFlow(previousData)
    internal val scope: CoroutineScope = newScope

    fun <NewData> mapData(
        transform: suspend (Data?) -> NewData?
    ) = flowState
        .mapData(transform)
        .catch {
            flowState.emitError(it)
        }

    override val replayCache: List<Response<Data>>
        get() = flowState.replayCache
    override val value: Response<Data>
        get() = flowState.value

    override suspend fun collect(collector: FlowCollector<Response<Data>>)
        = flowState.collect(collector)

    suspend fun collect(
        dataCollector: FlowCollector<Data>,
        errorCollector: FlowCollector<Throwable>
    ) {
        flowState.collect {
            it.data?.let { data ->
                dataCollector.emit(data)
            }
            it.error?.let { error ->
                errorCollector.emit(error)
            }
        }
    }

    suspend fun collectLatestData(
        dataCollector: FlowCollector<Data>,
        errorCollector: FlowCollector<Throwable>
    ) {
        flowState.collectLatest {
            it.data?.let { data ->
                dataCollector.emit(data)
            }
            it.error?.let { error ->
                errorCollector.emit(error)
            }
        }
    }

    internal constructor(
        value: Response<Data>,
        scope: CoroutineScope,
        mirror: Flow<Response<Data>>
    ) : this(value, scope) {
        scope.launch { mirror.collect(flowState::tryEmit) }
    }

    fun shareIn(scope: CoroutineScope, started: SharingStarted, replay: Int = 0) = ResponseStateFlow(
        value = value,
        scope = scope,
        mirror = flowState.shareIn(scope, started, replay)
    )

}

fun <Data> MutableResponseStateFlow<Data>.asResponseStateFlow() = ResponseStateFlow(
    value,
    scope,
    flowState
)