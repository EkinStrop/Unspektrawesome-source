package com.unspektrawesome.spektra

import android.content.SharedPreferences

interface SpektraStatePersistence {
    fun load(): SpektraState?
    fun save(state: SpektraState)
}

class SharedPreferencesSpektraStatePersistence(
    private val preferences: SharedPreferences,
    private val key: String = DEFAULT_KEY,
) : SpektraStatePersistence {
    override fun load(): SpektraState? = preferences.getString(key, null)?.let(SpektraStateJson::decode)

    override fun save(state: SpektraState) {
        preferences.edit().putString(key, SpektraStateJson.encode(state)).apply()
    }

    companion object {
        const val DEFAULT_KEY = "spektra_state_json"
    }
}

object InMemorySpektraStatePersistence : SpektraStatePersistence {
    override fun load(): SpektraState? = null
    override fun save(state: SpektraState) = Unit
}
