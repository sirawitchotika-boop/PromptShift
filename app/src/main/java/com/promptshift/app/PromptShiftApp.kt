package com.promptshift.app

import android.app.Application
import android.content.Context
import com.promptshift.app.ai.GenerationCoordinator
import com.promptshift.app.ai.HttpOpenAiStreamingClient
import com.promptshift.app.data.AiConfigRepository
import com.promptshift.app.data.AndroidKeystoreApiKeyStore
import com.promptshift.app.data.DefaultAiConfigRepository
import com.promptshift.app.data.DefaultPromptBatchRepository
import com.promptshift.app.data.FileAiConfigPersistence
import com.promptshift.app.data.FileBatchPersistence
import com.promptshift.app.data.PromptBatchRepository
import com.promptshift.app.timer.CountdownTimerManager
import com.promptshift.app.timer.TimerAlertNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class PromptShiftApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var repository: PromptBatchRepository
        private set

    lateinit var aiConfigRepository: AiConfigRepository
        private set

    lateinit var generationCoordinator: GenerationCoordinator
        private set

    lateinit var timerManager: CountdownTimerManager
        private set

    private val _isOverlayRunning = MutableStateFlow(false)
    val isOverlayRunning: StateFlow<Boolean> = _isOverlayRunning.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        val batchFile = File(filesDir, "promptshift_state.json")
        repository = DefaultPromptBatchRepository(FileBatchPersistence(batchFile))

        val aiConfigFile = File(filesDir, "promptshift_ai_config.json")
        aiConfigRepository = DefaultAiConfigRepository(
            persistence = FileAiConfigPersistence(aiConfigFile),
            apiKeyStore = AndroidKeystoreApiKeyStore(this),
        )

        generationCoordinator = GenerationCoordinator(
            appScope = appScope,
            batchRepository = repository,
            configRepository = aiConfigRepository,
            streamingClient = HttpOpenAiStreamingClient(),
        )

        timerManager = CountdownTimerManager(
            appScope = appScope,
            onTimerFinished = {
                TimerAlertNotifier.triggerTimerDoneAlert(this)
                repository.postFeedback("Timer Done! ⏱", isError = false)
            },
            onAlertDismissed = {
                TimerAlertNotifier.dismissAlert(this)
            },
        )
    }

    fun setOverlayRunning(running: Boolean) {
        _isOverlayRunning.value = running
    }

    companion object {
        fun from(context: Context): PromptShiftApp =
            context.applicationContext as PromptShiftApp
    }
}
