package com.unspektrawesome.spektra

import java.util.ArrayDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The single authoritative source of versioned SpektraFilm processing state. */
class SpektraStateRepository(
    private val persistence: SpektraStatePersistence,
    private val maxUndoDepth: Int = DEFAULT_UNDO_DEPTH,
) {
    private val lock = Any()
    private val undoHistory = ArrayDeque<SpektraState>()
    private val mutableState: MutableStateFlow<SpektraState>

    init {
        require(maxUndoDepth > 0) { "maxUndoDepth must be positive" }
        mutableState = MutableStateFlow(persistence.load() ?: SpektraState.DEFAULT)
    }

    val state: StateFlow<SpektraState> = mutableState.asStateFlow()

    fun update(transform: (SpektraState) -> SpektraState): SpektraState = synchronized(lock) {
        val current = mutableState.value
        val candidate = transform(current).copy(revisionId = current.revisionId)
        commit(current, candidate, recordUndo = true)
    }

    fun updateParams(transform: (SpektraFilmParams) -> SpektraFilmParams): SpektraState =
        update { state -> state.copy(params = transform(state.params)) }

    fun reset(): SpektraState = synchronized(lock) {
        val current = mutableState.value
        val candidate = SpektraState.DEFAULT.copy(revisionId = current.revisionId)
        commit(current, candidate, recordUndo = true)
    }

    fun undo(): Boolean = synchronized(lock) {
        if (undoHistory.isEmpty()) return@synchronized false
        val current = mutableState.value
        val previous = requireNotNull(undoHistory.peekLast()).copy(revisionId = current.revisionId)
        commit(current, previous, recordUndo = false)
        undoHistory.removeLast()
        true
    }

    fun canUndo(): Boolean = synchronized(lock) { undoHistory.isNotEmpty() }

    fun captureSnapshot(): SpektraCaptureSnapshot = synchronized(lock) {
        val current = mutableState.value
        SpektraCaptureSnapshot(current.revisionId, current)
    }

    private fun commit(
        current: SpektraState,
        candidate: SpektraState,
        recordUndo: Boolean,
    ): SpektraState {
        if (candidate.sameProcessingAs(current)) return current
        check(current.revisionId < Long.MAX_VALUE) { "Spektra revision counter exhausted" }

        val revised = candidate.copy(revisionId = current.revisionId + 1L)
        persistence.save(revised)

        if (recordUndo) {
            if (undoHistory.size == maxUndoDepth) undoHistory.removeFirst()
            undoHistory.addLast(current)
        }

        mutableState.value = revised
        return revised
    }

    companion object {
        const val DEFAULT_UNDO_DEPTH = 50
    }
}
