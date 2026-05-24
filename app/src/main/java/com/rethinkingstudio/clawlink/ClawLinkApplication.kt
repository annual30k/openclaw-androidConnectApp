package com.rethinkingstudio.clawlink

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rethinkingstudio.clawlink.core.domain.CredentialStore
import com.rethinkingstudio.clawlink.core.domain.NotificationPort
import com.rethinkingstudio.clawlink.core.models.SessionCredentials
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.network.transport.RelayWebSocketClient
import com.rethinkingstudio.clawlink.core.state.auth.AuthStore
import com.rethinkingstudio.clawlink.core.state.backup.BackupStore
import com.rethinkingstudio.clawlink.core.state.chat.ChatStore
import com.rethinkingstudio.clawlink.core.state.chat.ChatSessionSelectionStore
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.core.state.model.ModelStore
import com.rethinkingstudio.clawlink.core.state.skill.SkillStore
import com.rethinkingstudio.clawlink.core.state.task.TaskStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "clawlink_prefs")

class SecureCredentialStore(private val context: Context) : CredentialStore {
    companion object {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val RELAY_BASE_URL = stringPreferencesKey("relay_base_url")
        val LAST_GATEWAY_ID = stringPreferencesKey("last_gateway_id")
        private const val SECURE_PREFS_NAME = "clawlink_secure_prefs"
        private const val FALLBACK_PREFS_NAME = "clawlink_fallback_prefs"
        private const val TOKEN_KEY = "access_token"
        private const val RELAY_URL_KEY = "relay_base_url"
    }

    private val fallbackPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val credentialPrefs: SharedPreferences by lazy {
        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            context.deleteSharedPreferences(SECURE_PREFS_NAME)
            fallbackPrefs
        }
    }

    override suspend fun saveCredentials(credentials: SessionCredentials) {
        runCatching {
            credentialPrefs.edit()
                .putString(TOKEN_KEY, credentials.accessToken)
                .putString(RELAY_URL_KEY, credentials.relayBaseURL)
                .apply()
        }.getOrElse {
            fallbackPrefs.edit()
                .putString(TOKEN_KEY, credentials.accessToken)
                .putString(RELAY_URL_KEY, credentials.relayBaseURL)
                .apply()
        }
    }

    override suspend fun loadCredentials(): SessionCredentials? {
        val token = readCredentialValue(TOKEN_KEY)?.takeIf { it.isNotBlank() } ?: return null
        val url = readCredentialValue(RELAY_URL_KEY)?.takeIf { it.isNotBlank() } ?: return null
        return SessionCredentials(accessToken = token, relayBaseURL = url)
    }

    private fun readCredentialValue(key: String): String? {
        return runCatching { credentialPrefs.getString(key, null) }.getOrNull()
            ?: runCatching { fallbackPrefs.getString(key, null) }.getOrNull()
    }

    override suspend fun clearCredentials() {
        runCatching { credentialPrefs.edit().clear().apply() }
        fallbackPrefs.edit()
            .remove(TOKEN_KEY)
            .remove(RELAY_URL_KEY)
            .apply()
        context.dataStore.edit { it.remove(LAST_GATEWAY_ID) }
    }

    override suspend fun saveLastGatewayId(gatewayId: String) {
        context.dataStore.edit { it[LAST_GATEWAY_ID] = gatewayId }
    }

    override suspend fun loadLastGatewayId(): String? {
        return context.dataStore.data.map { it[LAST_GATEWAY_ID] }.firstOrNull()
    }
}

class AppContainer(context: Context) {
    val credentialStore: CredentialStore = SecureCredentialStore(context)
    val apiClient: RelayAPIClient = RelayAPIClient()
    val wsClient: RelayWebSocketClient = RelayWebSocketClient()
    val notificationPort: NotificationPort = AndroidNotificationPort(context)
    val chatSessionSelectionStore: ChatSessionSelectionStore = ChatSessionSelectionStore(context)

    val authStore: AuthStore = AuthStore(apiClient, credentialStore)
    val gatewayStore: GatewayStore = GatewayStore(apiClient, credentialStore, wsClient)
    val chatStore: ChatStore = ChatStore(apiClient, wsClient, notificationPort, chatSessionSelectionStore)
    val modelStore: ModelStore = ModelStore(apiClient)
    val skillStore: SkillStore = SkillStore(apiClient)
    val taskStore: TaskStore = TaskStore(apiClient)
    val backupStore: BackupStore = BackupStore(apiClient)
    val userPreferencesStore: com.rethinkingstudio.clawlink.core.state.UserPreferencesStore = com.rethinkingstudio.clawlink.core.state.UserPreferencesStore(context)
}

class ClawLinkApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
