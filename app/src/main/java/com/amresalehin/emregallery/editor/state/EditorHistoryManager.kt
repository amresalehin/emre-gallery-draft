package com.amresalehin.emregallery.editor.state

import com.amresalehin.emregallery.editor.EditSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages non-destructive undo and redo stacks, slider baselines, and history navigation.
 */
class EditorHistoryManager {

    private val undoStack = mutableListOf<EditSessionState>()
    private val redoStack = mutableListOf<EditSessionState>()
    private var sliderBaselineState: EditSessionState? = null

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        sliderBaselineState = null
        _canUndo.value = false
        _canRedo.value = false
    }

    fun recordSliderBaseline(current: EditSessionState) {
        if (sliderBaselineState == null) {
            sliderBaselineState = current
        }
    }

    fun commitSliderChange(current: EditSessionState) {
        val baseline = sliderBaselineState ?: return
        sliderBaselineState = null
        if (baseline != current) {
            undoStack.add(baseline)
            redoStack.clear()
            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = false
        }
    }

    fun pushState(previous: EditSessionState, next: EditSessionState) {
        commitSliderChange(previous)
        undoStack.add(previous)
        redoStack.clear()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = false
    }

    fun undo(current: EditSessionState): EditSessionState? {
        commitSliderChange(current)
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(current)
            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = true
            return prev
        }
        return null
    }

    fun redo(current: EditSessionState): EditSessionState? {
        commitSliderChange(current)
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(current)
            _canUndo.value = true
            _canRedo.value = redoStack.isNotEmpty()
            return next
        }
        return null
    }

    fun getHistoryList(current: EditSessionState): List<String> {
        return undoStack.map { it.description } + listOf(current.description)
    }

    fun getCurrentHistoryIndex(): Int = undoStack.size

    fun getFullHistory(current: EditSessionState): List<EditSessionState> {
        return undoStack + listOf(current)
    }

    fun hasHistory(): Boolean = undoStack.isNotEmpty()

    fun jumpToHistoryStep(current: EditSessionState, targetIndex: Int): EditSessionState? {
        val fullHistory = undoStack + listOf(current)
        if (targetIndex !in fullHistory.indices) return null
        commitSliderChange(current)
        val targetState = fullHistory[targetIndex]
        undoStack.clear()
        for (i in 0 until targetIndex) {
            undoStack.add(fullHistory[i])
        }
        redoStack.clear()
        for (i in fullHistory.lastIndex downTo targetIndex + 1) {
            redoStack.add(fullHistory[i])
        }
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        return targetState
    }
}
