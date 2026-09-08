package com.example.dovimu.platform

import android.content.Context

class AndroidSettingsStore(context: Context) : SettingsStore {
    private val prefs = context.getSharedPreferences("dovimu", Context.MODE_PRIVATE)

    override fun getBaseUrl(): String =
        prefs.getString(KEY, DEFAULT_API_URL)?.ifBlank { DEFAULT_API_URL } ?: DEFAULT_API_URL

    override fun setBaseUrl(url: String) {
        prefs.edit().putString(KEY, url).apply()
    }

    private companion object {
        const val KEY = "api_base_url"
    }
}
