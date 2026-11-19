package com.promptshift.app

import android.app.Application
import android.content.Context
import com.promptshift.app.data.DefaultPromptBatchRepository
import com.promptshift.app.data.FileBatchPersistence
import com.promptshift.app.data.PromptBatchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class PromptShiftApp : Application() {

    lateinit var repository: PromptBatchRepository
        private set

    private val _isOverlayRunning = MutableStateFlow(false)
    val isOverlayRunning: StateFlow<Boolean> = _isOverlayRunning.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        val storeFile = File(filesDir, "promptshift_state.json")
        repository = DefaultPromptBatchRepository(FileBatchPersistence(storeFile))
    }

    fun setOverlayRunning(running: Boolean) {
        _isOverlayRunning.value = running
    }

    companion object {
        fun from(context: Context): PromptShiftApp =
            context.applicationContext as PromptShiftApp
    }
}
