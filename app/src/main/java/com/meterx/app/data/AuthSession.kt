package com.meterx.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AuthUser(
    val id: String,
    val username: String,
)

class AuthSession(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "meterx_auth",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _user = MutableStateFlow(loadUser())
    val user: StateFlow<AuthUser?> = _user.asStateFlow()

    val token: String?
        get() = preferences.getString(KEY_TOKEN, null)

    fun save(token: String, user: AuthUser) {
        preferences.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, user.id)
            .putString(KEY_USERNAME, user.username)
            .apply()
        _user.value = user
    }

    fun clear() {
        preferences.edit().clear().apply()
        _user.value = null
    }

    private fun loadUser(): AuthUser? {
        val id = preferences.getString(KEY_USER_ID, null) ?: return null
        val username = preferences.getString(KEY_USERNAME, null) ?: return null
        if (preferences.getString(KEY_TOKEN, null) == null) return null
        return AuthUser(id, username)
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_USER_ID = "user_id"
        const val KEY_USERNAME = "username"
    }
}
