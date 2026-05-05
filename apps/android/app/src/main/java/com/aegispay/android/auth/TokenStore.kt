package com.aegispay.android.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted token store backed by EncryptedSharedPreferences
 * (AES256-SIV key encryption + AES256-GCM value encryption).
 *
 * All tokens are stored under the app's private encrypted prefs file —
 * never in plain SharedPreferences, never in external storage.
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "aegispay_auth_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // ── Keys ─────────────────────────────────────────────────────────────────

    private object Key {
        const val ACCESS_TOKEN   = "access_token"
        const val REFRESH_TOKEN  = "refresh_token"
        const val ID_TOKEN       = "id_token"
        const val EXPIRES_AT_MS  = "expires_at_ms"
        const val USER_ID        = "user_id"
        const val USER_ROLE      = "user_role"
        const val USER_EMAIL     = "user_email"
        const val USER_NAME      = "user_name"
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    var accessToken:  String? get() = prefs.getString(Key.ACCESS_TOKEN, null);  set(v) = put(Key.ACCESS_TOKEN, v)
    var refreshToken: String? get() = prefs.getString(Key.REFRESH_TOKEN, null); set(v) = put(Key.REFRESH_TOKEN, v)
    var idToken:      String? get() = prefs.getString(Key.ID_TOKEN, null);      set(v) = put(Key.ID_TOKEN, v)
    var userId:       String? get() = prefs.getString(Key.USER_ID, null);       set(v) = put(Key.USER_ID, v)
    var userRole:     String? get() = prefs.getString(Key.USER_ROLE, null);     set(v) = put(Key.USER_ROLE, v)
    var userEmail:    String? get() = prefs.getString(Key.USER_EMAIL, null);    set(v) = put(Key.USER_EMAIL, v)
    var userName:     String? get() = prefs.getString(Key.USER_NAME, null);     set(v) = put(Key.USER_NAME, v)

    var expiresAtMs: Long
        get() = prefs.getLong(Key.EXPIRES_AT_MS, 0L)
        set(v) = prefs.edit().putLong(Key.EXPIRES_AT_MS, v).apply()

    // ── Derived ───────────────────────────────────────────────────────────────

    /** True when the access token exists and has > 60 s left. */
    val isAccessTokenValid: Boolean
        get() = !accessToken.isNullOrBlank() &&
                expiresAtMs - Instant.now().toEpochMilli() > 60_000L

    val canRefresh: Boolean
        get() = !refreshToken.isNullOrBlank()

    // ── Mutations ─────────────────────────────────────────────────────────────

    fun store(
        accessToken:  String,
        refreshToken: String?,
        idToken:      String?,
        expiresInSec: Long,
        userId:       String?,
        userRole:     String?,
        userEmail:    String?,
        userName:     String?,
    ) {
        this.accessToken   = accessToken
        this.refreshToken  = refreshToken
        this.idToken       = idToken
        this.expiresAtMs   = Instant.now().toEpochMilli() + expiresInSec * 1_000L
        this.userId        = userId
        this.userRole      = userRole
        this.userEmail     = userEmail
        this.userName      = userName
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun put(key: String, value: String?) {
        if (value != null) prefs.edit().putString(key, value).apply()
        else               prefs.edit().remove(key).apply()
    }
}
