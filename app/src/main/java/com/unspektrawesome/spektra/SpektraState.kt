package com.unspektrawesome.spektra

/**
 * Versioned application state for the exact SpektraFilm processing parameters.
 *
 * [revisionId] belongs to application state synchronization. Only [params] is serialized into
 * the 648-byte native SpektraFilm parameter block.
 */
data class SpektraState(
    val revisionId: Long = INITIAL_REVISION,
    val params: SpektraFilmParams = DEFAULT_PARAMS,
) {
    init {
        require(revisionId >= INITIAL_REVISION) { "revisionId must be non-negative" }
    }

    internal fun sameProcessingAs(other: SpektraState): Boolean = params == other.params

    companion object {
        const val INITIAL_REVISION = 0L
        val DEFAULT_PARAMS = SpektraFilmParams(enabled = true)
        val DEFAULT = SpektraState()
    }
}

/** A shutter-time value object that cannot change while capture processing is in flight. */
data class SpektraCaptureSnapshot(
    val revisionId: Long,
    val state: SpektraState,
) {
    init {
        require(revisionId == state.revisionId) { "Snapshot revision must match state revision" }
    }

    val params: SpektraFilmParams get() = state.params
}
