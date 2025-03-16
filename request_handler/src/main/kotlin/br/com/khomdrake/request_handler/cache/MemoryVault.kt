package br.com.khomdrake.request_handler.cache

object MemoryVault {

    private val data = mutableMapOf<String, Any>()

    suspend fun setData(key: String, newData: Any) {
        data[key] = newData
    }

    suspend fun getDataLong(key: String) = getData<Long>(key)

    suspend fun getDataInt(key: String) = getData<Int>(key)

    suspend fun getDataString(key: String) = getData<String>(key)

    suspend fun getDataBoolean(key: String) = getData<Boolean>(key)

    suspend fun <T> getData(key: String) : T? {
        return runCatching { data[key] as? T }.getOrNull()
    }

    fun clearCache() {
        data.clear()
    }

}