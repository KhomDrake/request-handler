package br.com.khomdrake.request_handler.data

enum class ResponseStatus {
    LOADING,
    ERROR,
    SUCCESS
}

data class Response<T>(
    val state: ResponseStatus = ResponseStatus.LOADING,
    val data: T? = null,
    val error: Throwable? = null
)

fun <Data> responseLoading() = Response<Data>()

fun <Data> responseData(data: Data?) = Response<Data>(
    state = ResponseStatus.SUCCESS,
    data = data
)

fun <Data> responseError(throwable: Throwable?) = Response<Data>(
    state = ResponseStatus.ERROR,
    error = throwable
)