package br.com.khomdrake.request_handler.cache

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

private const val DATA_STORE_KEY = "93823kjdhaksj"
private val diskVaultScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

object DiskVault {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = DATA_STORE_KEY,
        scope = diskVaultScope
    )
    private var context: WeakReference<Context> = WeakReference(null)

    fun init(ctx: Context) {
        context = WeakReference(ctx)
        context.get()?.dataStore
    }

    private val dataStore: DataStore<Preferences>?
        get() = context.get()?.dataStore

    suspend fun setValue(key: String, value: String) {
        dataStore?.apply {
            val newKey = stringPreferencesKey(key)
            dataStore?.edit { settings ->
                settings[newKey] = value
            }
        }
    }

    suspend fun setValue(key: String, value: Long) {
        dataStore?.apply {
            val newKey = longPreferencesKey(key)
            dataStore?.edit { settings ->
                settings[newKey] = value
            }
        }
    }

    suspend fun containsKey(key: String) = dataStore?.data?.map { settings ->
        settings.contains(stringPreferencesKey(key))
    }?.first() ?: false

    suspend fun setValue(key: String, value: Boolean) {
        dataStore?.apply {
            val newKey = booleanPreferencesKey(key)
            dataStore?.edit { settings ->
                settings[newKey] = value
            }
        }
    }

    suspend fun getValue(key: String) : String? {
        return dataStore?.data?.map { settings: Preferences ->
            val newKey = stringPreferencesKey(key)
            settings[newKey]
        }?.first()
    }

    suspend fun getValueLong(key: String) : Long? {
        return dataStore?.data?.map { settings: Preferences ->
            val newKey = longPreferencesKey(key)
            settings[newKey]
        }?.first()
    }

    fun clearCache() {
        runCatching {
            diskVaultScope.launch {
                dataStore?.edit {
                    it.clear()
                }
            }
        }
    }

}