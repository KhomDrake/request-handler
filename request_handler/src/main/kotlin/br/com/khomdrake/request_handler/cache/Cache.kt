package br.com.khomdrake.request_handler.cache

import br.com.khomdrake.request_handler.CacheType
import br.com.khomdrake.request_handler.log.LogHandler
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class Cache<Data> internal constructor(
    private val mainTag: String,
    private var key: String = mainTag
) {

    private var type = CacheType.DISK
    private var timeout: Duration = 10.minutes
    private var retrieve: (suspend (String) -> Data?)? = null
    private var remove: (suspend (String) -> Boolean)? = null
    private var save: (suspend (String, Data) -> Boolean)? = null

    private suspend fun saveExpirationDate() {
        val expirationDate = System.currentTimeMillis() + timeout.inWholeMilliseconds

        if(type == CacheType.DISK) {
            DiskVault.setValue(key, expirationDate)
        } else {
            MemoryVault.setData(key, expirationDate)
        }
    }

    fun setCacheKey(newKey: String) = apply {
        key = newKey
    }

    fun save(function: (suspend (key: String, data: Data) -> Boolean)?) = apply {
        save = function
    }

    fun retrieve(function: suspend (key: String) -> Data?) = apply {
        retrieve = function
    }

    fun remove(function: suspend (key: String) -> Boolean) = apply {
        remove = function
    }

    fun timeout(
        duration: Duration,
        newType: CacheType
    ) = apply {
        timeout = duration
        type = newType
    }

    private fun logInfo(info: String) {
        LogHandler.logInfo(mainTag, info)
    }

    suspend fun save(data: Data) = apply {
        kotlin.runCatching {
            save?.invoke(key, data)
        }
            .onFailure {
                logInfo(
                    """
                            [Cache] Error while trying to save cache with
                                key: $key
                                data: $data

                                error: ${it.stackTraceToString()}
                        """.trimIndent()
                )
            }
            .onSuccess {
                val savedSuccessfully = it ?: false

                if(savedSuccessfully) {
                    saveExpirationDate()
                    logInfo("[Cache] cache saved key: $key")
                    logInfo("[Cache] cache saved data: $data")
                } else {
                    logInfo(
                        """
                                [Cache] Cache not saved successfully with
                                    key: $key
                                    data: $data
                            """.trimIndent()
                    )
                }
            }
    }

    suspend fun retrieve() = retrieve?.invoke(key)

    suspend fun remove() = apply {
        kotlin.runCatching {
            remove?.invoke(key)
        }.onSuccess {
            val savedSuccessfully = it ?: false
            if(savedSuccessfully)
                logInfo(
                    """
                        [Cache] Cache removed successfully for key $key
                    """.trimIndent()
                )
            else
                logInfo(
                    """
                        [Cache] Cache removed unsuccessfully for key $key
                    """.trimIndent()
                )

        }.onFailure {
            logInfo(
                "[Cache] Failure to remove cache for $key with error: \n ${it.stackTraceToString()}"
            )
        }
    }

    suspend fun isExpired(currentTime: Long) : Boolean {
        val expirationDate = (
            if(type == CacheType.DISK) DiskVault.getValueLong(key)
            else MemoryVault.getDataLong(key)
        ) ?: return true

        return currentTime >= expirationDate
    }

}
