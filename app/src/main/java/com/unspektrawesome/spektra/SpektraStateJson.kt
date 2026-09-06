package com.unspektrawesome.spektra

import org.json.JSONObject

/** Stable JSON envelope for the exact SpektraFilm parameter model. */
internal object SpektraStateJson {
    private const val SCHEMA_VERSION = 2

    fun encode(state: SpektraState): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("revisionId", state.revisionId)
        put("params", state.params.toJsonObject())
    }.toString()

    fun decode(json: String): SpektraState? = runCatching {
        val value = JSONObject(json)
        if (value.optInt("schemaVersion", -1) != SCHEMA_VERSION) return null
        val revisionId = value.getLong("revisionId")
        val params = SpektraFilmParams.fromJson(value.optJSONObject("params"))
        SpektraState(revisionId = revisionId, params = params)
    }.getOrNull()
}
