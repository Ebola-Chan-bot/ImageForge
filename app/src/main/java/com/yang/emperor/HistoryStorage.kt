package com.yang.emperor

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_HISTORY_ITEMS = 50

fun now(): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

fun secureConfigPreferences(context: Context): SharedPreferences {
    val appContext = context.applicationContext
    val legacyPrefs = appContext.getSharedPreferences(ConfigKeys.LEGACY_PREFS_NAME, Context.MODE_PRIVATE)

    val securePrefs = createEncryptedPreferencesOrNull(appContext)
        ?: return legacyPrefs

    migrateLegacyConfigIfNeeded(legacyPrefs, securePrefs)
    return securePrefs
}

fun loadHistory(prefs: SharedPreferences): List<HistoryItem> {
    val raw = prefs.getString(ConfigKeys.HISTORY, "[]") ?: "[]"
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { index ->
            val item = arr.optJSONObject(index) ?: return@mapNotNull null
            val time = item.optString("time").trim()
            val prompt = item.optString("prompt").trim()
            val state = item.optString("state", "success").ifBlank { "success" }

            if (time.isBlank() && prompt.isBlank()) {
                return@mapNotNull null
            }

            HistoryItem(
                time = time,
                mode = item.optString("mode"),
                model = item.optString("model"),
                prompt = prompt,
                path = item.optString("path"),
                state = state,
                error = item.optString("error", "")
            )
        }
    }.getOrElse {
        emptyList()
    }
}

fun saveHistory(prefs: SharedPreferences, items: List<HistoryItem>) {
    val arr = JSONArray()
    items.take(MAX_HISTORY_ITEMS).forEach { item ->
        arr.put(
            JSONObject()
                .put("time", item.time)
                .put("mode", item.mode)
                .put("model", item.model)
                .put("prompt", item.prompt)
                .put("path", item.path)
                .put("state", item.state.ifBlank { "success" })
                .put("error", item.error)
        )
    }
    prefs.edit { putString(ConfigKeys.HISTORY, arr.toString()) }
}

private fun createEncryptedPreferencesOrNull(context: Context): SharedPreferences? {
    return runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            ConfigKeys.SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrNull()
}

private fun migrateLegacyConfigIfNeeded(
    legacyPrefs: SharedPreferences,
    securePrefs: SharedPreferences
) {
    if (securePrefs.getBoolean(ConfigKeys.SECURE_MIGRATED_FROM_V16, false)) return
    val keys = ConfigKeys.CONFIG_MIGRATION_KEYS
    securePrefs.edit {
        keys.forEach { key ->
            val value = legacyPrefs.getString(key, null)
            if (value != null) {
                putString(key, value)
            }
        }
        putBoolean(ConfigKeys.SECURE_MIGRATED_FROM_V16, true)
    }
    legacyPrefs.edit {
        keys.forEach { key -> remove(key) }
    }
}
