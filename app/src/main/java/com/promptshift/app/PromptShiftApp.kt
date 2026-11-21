package com.promptshift.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.promptshift.app.ai.GenerationCoordinator
import com.promptshift.app.ai.HttpOpenAiStreamingClient
import com.promptshift.app.data.AiConfigRepository
import com.promptshift.app.data.AndroidKeystoreApiKeyStore
import com.promptshift.app.data.DefaultAiConfigRepository
import com.promptshift.app.data.DefaultPromptBatchRepository
import com.promptshift.app.data.FileAiConfigPersistence
import com.promptshift.app.data.FileBatchPersistence
import com.promptshift.app.data.PromptBatchRepository
import com.promptshift.app.overlay.TopLeftTimerPillController
import com.promptshift.app.timer.CountdownTimerManager
import com.promptshift.app.timer.TimerAlertNotifier
import com.promptshift.app.ui.MainActivity
import com.promptshift.app.ui.TimerPopupActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
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

    private var topLeftTimerPillController: TopLeftTimerPillController? = null

    private val _isOverlayRunning = MutableStateFlow(false)
    val isOverlayRunning: StateFlow<Boolean> = _isOverlayRunning.asStateFlow()

    private var startedTimerUiCount = 0
    private val _isTimerHostUiForeground = MutableStateFlow(false)

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
            onTimerStarted = { targetEndMs ->
                TimerAlertNotifier.showRunningNotification(this, targetEndMs)
            },
            onTimerCancelled = {
                TimerAlertNotifier.cancelRunningNotification(this)
            },
            onTimerFinished = {
                TimerAlertNotifier.triggerTimerDoneAlert(this)
                repository.postFeedback("Timer Done! ⏱", isError = false)
            },
            onAlertDismissed = {
                TimerAlertNotifier.dismissAlert(this)
            },
        )

        val pillController = TopLeftTimerPillController(
            context = this,
            onPillClicked = { TimerPopupActivity.launch(this) },
        )
        topLeftTimerPillController = pillController

        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) {
                    if (activity is MainActivity || activity is TimerPopupActivity) {
                        startedTimerUiCount++
                        _isTimerHostUiForeground.value = startedTimerUiCount > 0
                    }
                }

                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) {
                    if (activity is MainActivity || activity is TimerPopupActivity) {
                        startedTimerUiCount = (startedTimerUiCount - 1).coerceAtLeast(0)
                        _isTimerHostUiForeground.value = startedTimerUiCount > 0
                    }
                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )

        appScope.launch {
            combine(timerManager.state, _isTimerHostUiForeground) { timerState, isHostForeground ->
                timerState to !isHostForeground
            }.collect { (timerState, shouldShowPill) ->
                pillController.update(timerState, shouldShow = shouldShowPill)
            }
        }
    }

    fun setOverlayRunning(running: Boolean) {
        _isOverlayRunning.value = running
    }

    companion object {
        fun from(context: Context): PromptShiftApp =
            context.applicationContext as PromptShiftApp
    }
}
