package com.unspektrawesome.settings

import android.content.Context
import com.unspektrawesome.camera.RawFormat
import com.unspektrawesome.camera.RawOutput
import org.json.JSONArray
import org.json.JSONObject

enum class RawPreviewQuality(
    val label: String,
    val scale: Float,
) {
    LOW("Low", 0.25f),
    MEDIUM("Medium", 0.5f),
    HIGH("High", 0.75f),
    FULL("Full", 1f),
}

data class CameraPreferences(
    val previewQuality: RawPreviewQuality = RawPreviewQuality.MEDIUM,
    val enabledLensIds: Set<String> = emptySet(),
    val lensSelectionConfigured: Boolean = false,
    val defaultLensId: String? = null,
    val lensButtonRouteIds: List<String> = emptyList(),
    val lensButtonMappingConfigured: Boolean = false,
    val rawPreviewStreams: Map<String, RawStreamSelection> = emptyMap(),
)

data class RawStreamSelection(
    val format: RawFormat,
    val width: Int,
    val height: Int,
    val maximumResolutionMode: Boolean = false,
) {
    init {
        require(width > 0 && height > 0) { "RAW raster dimensions must be positive" }
    }

    fun matches(output: RawOutput): Boolean = format == output.format &&
        width == output.size.width && height == output.size.height &&
        maximumResolutionMode == output.maximumResolutionMode

    companion object {
        fun from(output: RawOutput) = RawStreamSelection(
            output.format,
            output.size.width,
            output.size.height,
            output.maximumResolutionMode,
        )
    }
}

class CameraPreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): CameraPreferences {
        val previewQuality = runCatching {
            RawPreviewQuality.valueOf(
                preferences.getString(KEY_PREVIEW_QUALITY, null) ?: RawPreviewQuality.MEDIUM.name,
            )
        }.getOrDefault(RawPreviewQuality.MEDIUM)
        return CameraPreferences(
            previewQuality = previewQuality,
            enabledLensIds = preferences.getStringSet(KEY_ENABLED_LENSES, emptySet()).orEmpty().toSet(),
            lensSelectionConfigured = preferences.getBoolean(KEY_LENS_SELECTION_CONFIGURED, false),
            defaultLensId = preferences.getString(KEY_DEFAULT_LENS, null),
            lensButtonRouteIds = decodeLensButtons(
                preferences.getString(KEY_LENS_BUTTON_ROUTES, null),
            ),
            lensButtonMappingConfigured = preferences.getBoolean(
                KEY_LENS_BUTTON_MAPPING_CONFIGURED,
                false,
            ),
            rawPreviewStreams = decodeRawStreams(
                preferences.getString(KEY_RAW_PREVIEW_STREAMS, null),
            ),
        )
    }

    fun save(value: CameraPreferences) {
        preferences.edit()
            .putString(KEY_PREVIEW_QUALITY, value.previewQuality.name)
            .putStringSet(KEY_ENABLED_LENSES, value.enabledLensIds)
            .putBoolean(KEY_LENS_SELECTION_CONFIGURED, value.lensSelectionConfigured)
            .putString(KEY_DEFAULT_LENS, value.defaultLensId)
            .putString(KEY_LENS_BUTTON_ROUTES, encodeLensButtons(value.lensButtonRouteIds))
            .putBoolean(KEY_LENS_BUTTON_MAPPING_CONFIGURED, value.lensButtonMappingConfigured)
            .putString(KEY_RAW_PREVIEW_STREAMS, encodeRawStreams(value.rawPreviewStreams))
            .apply()
    }

    private fun encodeLensButtons(routeIds: List<String>): String = JSONArray().apply {
        routeIds.forEach(::put)
    }.toString()

    private fun decodeLensButtons(value: String?): List<String> = runCatching {
        val array = JSONArray(value ?: return emptyList())
        buildList {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeRawStreams(streams: Map<String, RawStreamSelection>): String =
        JSONObject().apply {
            streams.forEach { (routeId, selection) ->
                put(routeId, JSONObject().apply {
                    put(JSON_FORMAT, selection.format.name)
                    put(JSON_WIDTH, selection.width)
                    put(JSON_HEIGHT, selection.height)
                    put(JSON_MAXIMUM_MODE, selection.maximumResolutionMode)
                })
            }
        }.toString()

    private fun decodeRawStreams(value: String?): Map<String, RawStreamSelection> = runCatching {
        val root = JSONObject(value ?: return emptyMap())
        buildMap {
            root.keys().forEach { routeId ->
                val stream = root.optJSONObject(routeId) ?: return@forEach
                val format = runCatching {
                    RawFormat.valueOf(stream.getString(JSON_FORMAT))
                }.getOrNull() ?: return@forEach
                val width = stream.optInt(JSON_WIDTH)
                val height = stream.optInt(JSON_HEIGHT)
                if (routeId.isNotBlank() && width > 0 && height > 0) {
                    put(
                        routeId,
                        RawStreamSelection(
                            format,
                            width,
                            height,
                            stream.optBoolean(JSON_MAXIMUM_MODE, false),
                        ),
                    )
                }
            }
        }
    }.getOrDefault(emptyMap())

    private companion object {
        const val FILE_NAME = "camera_preferences"
        const val KEY_PREVIEW_QUALITY = "raw_preview_quality"
        const val KEY_ENABLED_LENSES = "enabled_lens_ids"
        const val KEY_LENS_SELECTION_CONFIGURED = "lens_selection_configured"
        const val KEY_DEFAULT_LENS = "default_lens_id"
        const val KEY_LENS_BUTTON_ROUTES = "lens_button_route_ids"
        const val KEY_LENS_BUTTON_MAPPING_CONFIGURED = "lens_button_mapping_configured"
        const val KEY_RAW_PREVIEW_STREAMS = "raw_preview_streams"
        const val JSON_FORMAT = "format"
        const val JSON_WIDTH = "width"
        const val JSON_HEIGHT = "height"
        const val JSON_MAXIMUM_MODE = "maximumResolutionMode"
    }
}
