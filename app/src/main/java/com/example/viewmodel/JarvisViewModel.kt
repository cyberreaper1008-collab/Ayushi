package com.example.viewmodel

import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AssistantMode
import com.example.core.JarvisAiStatus
import com.example.core.UiState
import com.example.data.AiToolExecutionResult
import com.example.data.JarvisAiEngine
import com.example.data.JarvisAiEngine.Companion.intVal
import com.example.data.JarvisAiEngine.Companion.str
import com.example.data.JarvisFirestoreRepository
import com.example.model.ChatMessage
import com.example.model.ExamTrack
import com.example.model.HudTheme
import com.example.model.JarvisNote
import com.example.model.MemoryCategory
import com.example.model.PendingSensitiveAction
import com.example.model.PersonalMemory
import com.example.model.PreferredLanguage
import com.example.model.ProductivityTask
import com.example.model.ResearchResult
import com.example.model.RoutineType
import com.example.model.SmartRoutine
import com.example.model.StudyProgress
import com.example.model.StudyQuizQuestion
import com.example.model.TaskCategory
import com.example.model.TaskPriority
import com.example.model.UserProfile
import com.example.platform.DeviceIntegrationManager
import com.example.platform.DeviceTelemetry
import com.example.platform.InstalledAppInfo
import com.example.platform.PermissionStatusSummary
import com.example.platform.VoiceController
import com.example.platform.VoiceOption
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val TAG = "JarvisVM"
private const val STOP_TIMEOUT_MS = 5000L

enum class JarvisTab(val route: String, val title: String) {
    CORE("core", "Core"),
    PRODUCTIVITY("productivity", "Planner"),
    STUDY("study", "Study"),
    RESEARCH("research", "Research"),
    SETTINGS("settings", "Settings")
}

class JarvisViewModel(
    private val repository: JarvisFirestoreRepository,
    private val aiEngine: JarvisAiEngine,
    private val voiceController: VoiceController,
    private val deviceManager: DeviceIntegrationManager,
    private val currentUserId: String,
    private val initialDisplayName: String?
) : ViewModel() {

    val userProfile: StateFlow<UserProfile> = repository.observeProfile(currentUserId)
        .map { it ?: UserProfile(userId = currentUserId, displayName = initialDisplayName ?: "Commander") }
        .catch { error ->
            unwrapFirestoreOrThrow(error)
            emit(UserProfile(userId = currentUserId, displayName = initialDisplayName ?: "Commander"))
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            UserProfile(userId = currentUserId, displayName = initialDisplayName ?: "Commander")
        )

    val messagesState: StateFlow<UiState<List<ChatMessage>>> = repository.observeConversations(currentUserId)
        .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
        .catch { error -> emit(UiState.Error(unwrapFirestoreOrThrow(error))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val tasksState: StateFlow<UiState<List<ProductivityTask>>> = repository.observeTasks(currentUserId)
        .map<List<ProductivityTask>, UiState<List<ProductivityTask>>> { UiState.Success(it) }
        .catch { error -> emit(UiState.Error(unwrapFirestoreOrThrow(error))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val notesState: StateFlow<UiState<List<JarvisNote>>> = repository.observeNotes(currentUserId)
        .map<List<JarvisNote>, UiState<List<JarvisNote>>> { UiState.Success(it) }
        .catch { error -> emit(UiState.Error(unwrapFirestoreOrThrow(error))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val memoriesState: StateFlow<UiState<List<PersonalMemory>>> = repository.observeMemories(currentUserId)
        .map<List<PersonalMemory>, UiState<List<PersonalMemory>>> { UiState.Success(it) }
        .catch { error -> emit(UiState.Error(unwrapFirestoreOrThrow(error))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val routinesState: StateFlow<UiState<List<SmartRoutine>>> = repository.observeRoutines(currentUserId)
        .map<List<SmartRoutine>, UiState<List<SmartRoutine>>> { UiState.Success(it) }
        .catch { error -> emit(UiState.Error(unwrapFirestoreOrThrow(error))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val studyLogsState: StateFlow<UiState<List<StudyProgress>>> = repository.observeStudyLogs(currentUserId)
        .map<List<StudyProgress>, UiState<List<StudyProgress>>> { UiState.Success(it) }
        .catch { error -> emit(UiState.Error(unwrapFirestoreOrThrow(error))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    // Voice & Device State
    val isListening: StateFlow<Boolean> = voiceController.isListening
    val isSpeaking: StateFlow<Boolean> = voiceController.isSpeaking
    val rmsDb: StateFlow<Float> = voiceController.rmsDb
    val partialTranscript: StateFlow<String> = voiceController.partialTranscript
    val voiceError: StateFlow<String?> = voiceController.voiceError
    val availableVoices: StateFlow<List<VoiceOption>> = voiceController.availableVoices
    val selectedVoiceName: StateFlow<String?> = voiceController.selectedVoiceName

    private val _aiStatus = MutableStateFlow(JarvisAiStatus.SYSTEM_ONLINE)
    val aiStatus: StateFlow<JarvisAiStatus> = _aiStatus.asStateFlow()

    private val _assistantMode = MutableStateFlow(AssistantMode.ASSISTANT)
    val assistantMode: StateFlow<AssistantMode> = _assistantMode.asStateFlow()

    private val _speakerEnabled = MutableStateFlow(true)
    val speakerEnabled: StateFlow<Boolean> = _speakerEnabled.asStateFlow()

    private val _requestedTab = MutableStateFlow<JarvisTab?>(null)
    val requestedTab: StateFlow<JarvisTab?> = _requestedTab.asStateFlow()

    private val _pendingAction = MutableStateFlow<PendingSensitiveAction?>(null)
    val pendingAction: StateFlow<PendingSensitiveAction?> = _pendingAction.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _deviceTelemetry = MutableStateFlow(DeviceTelemetry(-1, false, "Checking"))
    val deviceTelemetry: StateFlow<DeviceTelemetry> = _deviceTelemetry.asStateFlow()

    private val _permissionStatus = MutableStateFlow(PermissionStatusSummary(false, false))
    val permissionStatus: StateFlow<PermissionStatusSummary> = _permissionStatus.asStateFlow()

    // Attachment State
    private val _attachedBitmap = MutableStateFlow<Bitmap?>(null)
    val attachedBitmap: StateFlow<Bitmap?> = _attachedBitmap.asStateFlow()

    private val _attachedDocName = MutableStateFlow<String?>(null)
    val attachedDocName: StateFlow<String?> = _attachedDocName.asStateFlow()

    private var attachedDocBytes: ByteArray? = null
    private var attachedDocMime: String? = null

    // Study State
    private val _studyOutput = MutableStateFlow<UiState<String>?>(null)
    val studyOutput: StateFlow<UiState<String>?> = _studyOutput.asStateFlow()

    private val _quizState = MutableStateFlow<UiState<List<StudyQuizQuestion>>?>(null)
    val quizState: StateFlow<UiState<List<StudyQuizQuestion>>?> = _quizState.asStateFlow()

    // Research State
    private val _researchState = MutableStateFlow<UiState<ResearchResult>?>(null)
    val researchState: StateFlow<UiState<ResearchResult>?> = _researchState.asStateFlow()

    // Daily Organizer Summary
    private val _dailyPlanSummary = MutableStateFlow<UiState<String>?>(null)
    val dailyPlanSummary: StateFlow<UiState<String>?> = _dailyPlanSummary.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(initialDisplayName)
        }
        viewModelScope.launch(Dispatchers.IO) {
            _deviceTelemetry.value = deviceManager.getDeviceTelemetry()
            _permissionStatus.value = deviceManager.getPermissionStatus()
        }
        viewModelScope.launch {
            userProfile.collect { profile ->
                voiceController.updateSettings(
                    language = PreferredLanguage.fromRaw(profile.preferredLanguage),
                    speed = profile.speechSpeed.toFloat(),
                    pitch = profile.speechPitch.toFloat()
                )
            }
        }
        viewModelScope.launch {
            voiceController.isListening.collect { listening ->
                if (listening) {
                    _aiStatus.value = JarvisAiStatus.LISTENING
                } else if (_aiStatus.value == JarvisAiStatus.LISTENING) {
                    _aiStatus.value = JarvisAiStatus.SYSTEM_ONLINE
                }
            }
        }
        viewModelScope.launch {
            voiceController.isSpeaking.collect { speaking ->
                if (speaking) {
                    _aiStatus.value = JarvisAiStatus.SPEAKING
                } else if (_aiStatus.value == JarvisAiStatus.SPEAKING) {
                    _aiStatus.value = JarvisAiStatus.SYSTEM_ONLINE
                }
            }
        }
    }

    fun refreshDeviceTelemetry() {
        viewModelScope.launch(Dispatchers.IO) {
            _deviceTelemetry.value = deviceManager.getDeviceTelemetry()
            _permissionStatus.value = deviceManager.getPermissionStatus()
        }
    }

    fun consumeRequestedTab() {
        _requestedTab.value = null
    }

    fun clearBanner() {
        _statusBanner.value = null
        voiceController.clearVoiceError()
    }

    fun setAssistantMode(mode: AssistantMode) {
        _assistantMode.value = mode
    }

    fun toggleSpeaker() {
        val next = !_speakerEnabled.value
        _speakerEnabled.value = next
        if (!next) {
            voiceController.stopSpeaking()
            if (_aiStatus.value == JarvisAiStatus.SPEAKING) {
                _aiStatus.value = JarvisAiStatus.SYSTEM_ONLINE
            }
        }
    }

    fun interruptSpeech() {
        voiceController.stopSpeaking()
        if (_aiStatus.value == JarvisAiStatus.SPEAKING) {
            _aiStatus.value = JarvisAiStatus.SYSTEM_ONLINE
        }
    }

    fun speakMessage(text: String) {
        if (!_speakerEnabled.value) {
            _speakerEnabled.value = true
        }
        _aiStatus.value = JarvisAiStatus.SPEAKING
        voiceController.speak(text)
    }

    fun isSpeechRecognitionAvailable(): Boolean = voiceController.isRecognitionAvailable()

    fun canLaunchSystemSpeechIntent(): Boolean = voiceController.canLaunchSystemSpeechIntent()

    fun createSystemSpeechIntent(): Intent =
        voiceController.createSystemSpeechIntent(PreferredLanguage.fromRaw(userProfile.value.preferredLanguage))

    fun startVoiceListening() {
        voiceController.startListening(PreferredLanguage.fromRaw(userProfile.value.preferredLanguage))
    }

    fun stopVoiceListening() {
        voiceController.stopListening()
    }

    fun cancelVoiceListening() {
        voiceController.cancelListening()
        if (_aiStatus.value == JarvisAiStatus.LISTENING) {
            _aiStatus.value = JarvisAiStatus.SYSTEM_ONLINE
        }
    }

    fun submitPartialTranscriptNow() {
        val text = partialTranscript.value.trim()
        voiceController.cancelListening()
        if (text.isNotEmpty()) {
            onVoiceTranscript(text)
        }
    }

    fun onMicrophonePermissionDenied(permanentlyDenied: Boolean) {
        _statusBanner.value = if (permanentlyDenied) {
            "Microphone permission denied. Enable it in App Settings to speak to JARVIS."
        } else {
            "Microphone access is needed for voice commands."
        }
    }

    fun onVoiceTranscript(rawTranscript: String) {
        val cleaned = if (userProfile.value.wakeWordEnabled) {
            rawTranscript
                .replace(Regex("^(hey\\s+)?jarvis[,:\\s]*", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifEmpty { rawTranscript }
        } else {
            rawTranscript
        }

        // Check if transcript matches an enabled routine trigger phrase
        val routines = (routinesState.value as? UiState.Success)?.data.orEmpty()
        val matchedRoutine = routines.firstOrNull {
            it.enabled && cleaned.contains(it.triggerPhrase, ignoreCase = true)
        }
        if (matchedRoutine != null) {
            triggerRoutine(matchedRoutine)
            return
        }

        sendUserMessage(cleaned)
    }

    fun onSpeechFinished() {
        if (_aiStatus.value == JarvisAiStatus.SPEAKING) {
            _aiStatus.value = JarvisAiStatus.SYSTEM_ONLINE
        }
        if (userProfile.value.continuousVoice && _permissionStatus.value.microphoneGranted) {
            startVoiceListening()
        }
    }

    fun attachBitmap(bitmap: Bitmap?, label: String = "Attached Image") {
        _attachedBitmap.value = bitmap
        if (bitmap != null) {
            _attachedDocName.value = label
            attachedDocBytes = null
            attachedDocMime = null
        } else if (attachedDocBytes == null) {
            _attachedDocName.value = null
        }
    }

    fun attachDocument(bytes: ByteArray, mimeType: String, displayName: String) {
        if (bytes.size > 18 * 1024 * 1024) {
            _statusBanner.value = "Document exceeds 18MB limit for inline Gemini analysis."
            return
        }
        _attachedBitmap.value = null
        attachedDocBytes = bytes
        attachedDocMime = mimeType
        _attachedDocName.value = displayName
    }

    fun clearAttachment() {
        _attachedBitmap.value = null
        attachedDocBytes = null
        attachedDocMime = null
        _attachedDocName.value = null
    }

    fun sendUserMessage(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty() && _attachedBitmap.value == null && attachedDocBytes == null) return
        val effectivePrompt = prompt.ifEmpty { "Please analyze the attached file." }

        val bitmapToSend = _attachedBitmap.value
        val docBytesToSend = attachedDocBytes
        val docMimeToSend = attachedDocMime
        val attachmentLabel = _attachedDocName.value
        clearAttachment()

        // Interrupt any active TTS
        voiceController.stopSpeaking()

        viewModelScope.launch {
            _aiStatus.value = JarvisAiStatus.THINKING
            val mode = _assistantMode.value

            val userMsg = ChatMessage(
                userId = currentUserId,
                role = "user",
                text = effectivePrompt,
                mode = mode.name,
                attachmentLabel = attachmentLabel
            )
            repository.addMessage(userMsg)

            val history = (messagesState.value as? UiState.Success)?.data.orEmpty()
            val memories = (memoriesState.value as? UiState.Success)?.data.orEmpty()
            val tasks = (tasksState.value as? UiState.Success)?.data.orEmpty()

            try {
                _aiStatus.value = JarvisAiStatus.PROCESSING
                val output = aiEngine.sendAssistantTurn(
                    prompt = effectivePrompt,
                    history = history,
                    profile = userProfile.value,
                    memories = memories,
                    tasks = tasks,
                    mode = mode,
                    attachedBitmap = bitmapToSend,
                    attachedDocBytes = docBytesToSend,
                    attachedDocMime = docMimeToSend,
                    toolExecutor = { call -> executeAiFunctionCall(call) }
                )

                val modelMsg = ChatMessage(
                    userId = currentUserId,
                    role = "model",
                    text = output.replyText,
                    mode = mode.name,
                    actionSummary = output.actionSummaries.takeIf { it.isNotEmpty() }?.joinToString(" • ")
                )
                repository.addMessage(modelMsg)

                _aiStatus.value = if (output.actionSummaries.isNotEmpty()) {
                    JarvisAiStatus.TASK_COMPLETED
                } else {
                    JarvisAiStatus.SYSTEM_ONLINE
                }

                if (_speakerEnabled.value) {
                    _aiStatus.value = JarvisAiStatus.SPEAKING
                    voiceController.speak(output.replyText)
                } else if (_aiStatus.value == JarvisAiStatus.TASK_COMPLETED) {
                    delay(2000)
                    if (_aiStatus.value == JarvisAiStatus.TASK_COMPLETED) {
                        _aiStatus.value = JarvisAiStatus.SYSTEM_ONLINE
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI request timed out", e)
                _aiStatus.value = JarvisAiStatus.SYSTEM_ONLINE
                val errText = "Request timed out after 60 seconds. Please verify your connection and try again."
                _statusBanner.value = errText
                repository.addMessage(ChatMessage(userId = currentUserId, role = "model", text = errText, mode = mode.name))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI inference failed", e)
                _aiStatus.value = JarvisAiStatus.SYSTEM_ONLINE
                val errText = "JARVIS Engine Error: ${e.localizedMessage ?: "Unable to reach Gemini AI Logic."}"
                _statusBanner.value = errText
                repository.addMessage(ChatMessage(userId = currentUserId, role = "model", text = errText, mode = mode.name))
            }
        }
    }

    private suspend fun executeAiFunctionCall(call: FunctionCallPart): AiToolExecutionResult {
        val args = call.args
        val confirmSensitive = userProfile.value.confirmSensitiveActions

        return when (call.name) {
            "create_task_or_reminder" -> {
                val title = args.str("title").orEmpty().ifBlank { "Untitled Task" }
                val details = args.str("details").orEmpty()
                val category = TaskCategory.fromRaw(args.str("category"))
                val priority = TaskPriority.fromRaw(args.str("priority"))
                val dueLabel = args.str("dueLabel").orEmpty().ifBlank { "Today" }
                val res = repository.addTask(
                    ProductivityTask(
                        userId = currentUserId,
                        title = title,
                        details = details,
                        category = category.name,
                        priority = priority.name,
                        completed = false,
                        dueLabel = dueLabel
                    )
                )
                if (res.isSuccess) {
                    AiToolExecutionResult(
                        responsePayload = JarvisAiEngine.successJson("Saved ${category.name} '$title' ($priority, due: $dueLabel)."),
                        executedSummary = "Added ${category.name.lowercase()}: $title"
                    )
                } else {
                    AiToolExecutionResult(
                        JarvisAiEngine.errorJson(res.exceptionOrNull()?.localizedMessage ?: "Failed to save task.")
                    )
                }
            }

            "save_note" -> {
                val title = args.str("title").orEmpty().ifBlank { "JARVIS Note" }
                val content = args.str("content").orEmpty().ifBlank { "..." }
                val tag = args.str("tag").orEmpty().ifBlank { "General" }
                val res = repository.addNote(
                    JarvisNote(userId = currentUserId, title = title, content = content, tag = tag)
                )
                if (res.isSuccess) {
                    AiToolExecutionResult(
                        responsePayload = JarvisAiEngine.successJson("Saved note '$title' under tag '$tag'."),
                        executedSummary = "Saved note: $title"
                    )
                } else {
                    AiToolExecutionResult(
                        JarvisAiEngine.errorJson(res.exceptionOrNull()?.localizedMessage ?: "Failed to save note.")
                    )
                }
            }

            "save_memory" -> {
                val keyLabel = args.str("keyLabel").orEmpty().ifBlank { "Preference" }
                val fact = args.str("fact").orEmpty()
                val category = MemoryCategory.fromRaw(args.str("category")).name
                if (fact.isBlank()) {
                    return AiToolExecutionResult(JarvisAiEngine.errorJson("Memory fact cannot be empty."))
                }
                if (userProfile.value.autoSaveMemories && !confirmSensitive) {
                    val res = repository.addMemory(
                        PersonalMemory(
                            userId = currentUserId,
                            keyLabel = keyLabel,
                            fact = fact,
                            category = category,
                            approved = true
                        )
                    )
                    if (res.isSuccess) {
                        AiToolExecutionResult(
                            JarvisAiEngine.successJson("Saved memory '$keyLabel': $fact"),
                            executedSummary = "Memory saved: $keyLabel"
                        )
                    } else {
                        AiToolExecutionResult(JarvisAiEngine.errorJson("Could not save memory."))
                    }
                } else {
                    _pendingAction.value = PendingSensitiveAction.SaveMemoryAction(keyLabel, fact, category)
                    AiToolExecutionResult(
                        JarvisAiEngine.pendingJson("Confirmation dialog displayed to user to approve saving memory '$keyLabel'."),
                        executedSummary = "Pending memory approval: $keyLabel"
                    )
                }
            }

            "open_app" -> {
                val appName = args.str("appName").orEmpty()
                val matches = deviceManager.findInstalledApps(appName)
                val match = matches.firstOrNull()
                if (match == null) {
                    AiToolExecutionResult(JarvisAiEngine.errorJson("No installed app matching '$appName' was found."))
                } else if (confirmSensitive) {
                    _pendingAction.value = PendingSensitiveAction.OpenAppAction(match.label, match.packageName)
                    AiToolExecutionResult(
                        JarvisAiEngine.pendingJson("Waiting for user confirmation to open ${match.label}."),
                        executedSummary = "Awaiting approval: Open ${match.label}"
                    )
                } else {
                    val res = deviceManager.launchApp(match.packageName)
                    if (res.isSuccess) {
                        AiToolExecutionResult(
                            JarvisAiEngine.successJson("Opened ${match.label}."),
                            executedSummary = "Opened ${match.label}"
                        )
                    } else {
                        AiToolExecutionResult(JarvisAiEngine.errorJson(res.exceptionOrNull()?.localizedMessage ?: "Failed to launch app."))
                    }
                }
            }

            "set_alarm_or_timer" -> {
                val timerSeconds = args.intVal("timerSeconds")
                val hour = args.intVal("hour") ?: 7
                val minute = args.intVal("minute") ?: 0
                val label = args.str("label").orEmpty().ifBlank { "JARVIS Reminder" }
                val desc = if (timerSeconds != null && timerSeconds > 0) {
                    "Start a ${timerSeconds}s timer labeled \"$label\"?"
                } else {
                    "Set an alarm for %02d:%02d labeled \"$label\"?".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
                }
                if (confirmSensitive) {
                    _pendingAction.value = PendingSensitiveAction.SetAlarmAction(hour, minute, label, timerSeconds, description = desc)
                    AiToolExecutionResult(
                        JarvisAiEngine.pendingJson("Awaiting user confirmation to set clock alarm/timer."),
                        executedSummary = "Awaiting approval: Alarm/Timer"
                    )
                } else {
                    val res = if (timerSeconds != null && timerSeconds > 0) {
                        deviceManager.setTimer(timerSeconds, label)
                    } else {
                        deviceManager.setAlarm(hour, minute, label)
                    }
                    if (res.isSuccess) {
                        AiToolExecutionResult(
                            JarvisAiEngine.successJson(res.getOrDefault("Alarm/Timer set.")),
                            executedSummary = res.getOrDefault("Alarm/Timer set.")
                        )
                    } else {
                        AiToolExecutionResult(JarvisAiEngine.errorJson(res.exceptionOrNull()?.localizedMessage ?: "Unable to open Clock app."))
                    }
                }
            }

            "open_website" -> {
                val url = args.str("url").orEmpty()
                if (url.isBlank()) {
                    AiToolExecutionResult(JarvisAiEngine.errorJson("URL cannot be empty."))
                } else if (confirmSensitive) {
                    _pendingAction.value = PendingSensitiveAction.OpenUrlAction(url)
                    AiToolExecutionResult(JarvisAiEngine.pendingJson("Waiting for user confirmation to open $url."))
                } else {
                    val res = deviceManager.openWebsite(url)
                    if (res.isSuccess) {
                        AiToolExecutionResult(JarvisAiEngine.successJson("Opened $url"), "Opened website")
                    } else {
                        AiToolExecutionResult(JarvisAiEngine.errorJson(res.exceptionOrNull()?.localizedMessage ?: "Failed to open URL."))
                    }
                }
            }

            "start_navigation" -> {
                val dest = args.str("destination").orEmpty()
                if (dest.isBlank()) {
                    AiToolExecutionResult(JarvisAiEngine.errorJson("Destination is required."))
                } else if (confirmSensitive) {
                    _pendingAction.value = PendingSensitiveAction.NavigationAction(dest)
                    AiToolExecutionResult(JarvisAiEngine.pendingJson("Awaiting confirmation to start navigation to $dest."))
                } else {
                    val res = deviceManager.startNavigation(dest)
                    if (res.isSuccess) {
                        AiToolExecutionResult(JarvisAiEngine.successJson("Navigation started to $dest"), "Navigation: $dest")
                    } else {
                        AiToolExecutionResult(JarvisAiEngine.errorJson(res.exceptionOrNull()?.localizedMessage ?: "No maps app available."))
                    }
                }
            }

            "open_device_settings" -> {
                val settingType = args.str("settingType").orEmpty().ifBlank { "general" }
                if (confirmSensitive) {
                    _pendingAction.value = PendingSensitiveAction.OpenSettingsAction(settingType)
                    AiToolExecutionResult(JarvisAiEngine.pendingJson("Awaiting confirmation to open $settingType settings."))
                } else {
                    val res = deviceManager.openSettings(settingType)
                    if (res.isSuccess) {
                        AiToolExecutionResult(JarvisAiEngine.successJson("Opened $settingType settings."), "Opened $settingType settings")
                    } else {
                        AiToolExecutionResult(JarvisAiEngine.errorJson("Failed to open settings."))
                    }
                }
            }

            "share_text" -> {
                val text = args.str("text").orEmpty()
                if (text.isBlank()) {
                    AiToolExecutionResult(JarvisAiEngine.errorJson("Text cannot be empty."))
                } else if (confirmSensitive) {
                    _pendingAction.value = PendingSensitiveAction.ShareTextAction(text)
                    AiToolExecutionResult(JarvisAiEngine.pendingJson("Awaiting confirmation to open share sheet."))
                } else {
                    val res = deviceManager.shareText("Shared from JARVIS", text)
                    if (res.isSuccess) {
                        AiToolExecutionResult(JarvisAiEngine.successJson("Share sheet opened."), "Shared text")
                    } else {
                        AiToolExecutionResult(JarvisAiEngine.errorJson("Could not open share sheet."))
                    }
                }
            }

            "get_device_status" -> {
                val (t, appsCount) = withContext(Dispatchers.IO) {
                    val telemetry = deviceManager.getDeviceTelemetry()
                    val count = deviceManager.findInstalledApps().size
                    telemetry to count
                }
                _deviceTelemetry.value = t
                val payload = JsonObject(
                    mapOf(
                        "batteryPercent" to JsonPrimitive(t.batteryPercent),
                        "charging" to JsonPrimitive(t.charging),
                        "networkStatus" to JsonPrimitive(t.networkStatus),
                        "launchableAppsCount" to JsonPrimitive(appsCount)
                    )
                )
                AiToolExecutionResult(payload, "Read device telemetry")
            }

            "open_jarvis_module" -> {
                val mod = args.str("moduleName").orEmpty().lowercase()
                val tab = when (mod) {
                    "productivity", "planner" -> JarvisTab.PRODUCTIVITY
                    "study" -> JarvisTab.STUDY
                    "research" -> JarvisTab.RESEARCH
                    "settings" -> JarvisTab.SETTINGS
                    else -> null
                }
                if (tab != null) {
                    _requestedTab.value = tab
                    AiToolExecutionResult(JarvisAiEngine.successJson("Navigated to ${tab.title} module."), "Opened ${tab.title}")
                } else {
                    AiToolExecutionResult(JarvisAiEngine.errorJson("Unknown module '$mod'."))
                }
            }

            else -> AiToolExecutionResult(JarvisAiEngine.errorJson("Unknown tool ${call.name}"))
        }
    }

    fun confirmPendingAction() {
        val action = _pendingAction.value ?: return
        _pendingAction.value = null
        viewModelScope.launch {
            val message = when (action) {
                is PendingSensitiveAction.OpenAppAction ->
                    deviceManager.launchApp(action.packageName).fold({ it }, { it.localizedMessage ?: "Failed to launch app." })

                is PendingSensitiveAction.OpenUrlAction ->
                    deviceManager.openWebsite(action.url).fold({ it }, { it.localizedMessage ?: "Failed to open URL." })

                is PendingSensitiveAction.SetAlarmAction -> {
                    val res = if (action.timerSeconds != null && action.timerSeconds > 0) {
                        deviceManager.setTimer(action.timerSeconds, action.label)
                    } else {
                        deviceManager.setAlarm(action.hour, action.minute, action.label)
                    }
                    res.fold({ it }, { it.localizedMessage ?: "Failed to set alarm/timer." })
                }

                is PendingSensitiveAction.NavigationAction ->
                    deviceManager.startNavigation(action.destination).fold({ it }, { it.localizedMessage ?: "Failed to start navigation." })

                is PendingSensitiveAction.OpenSettingsAction ->
                    deviceManager.openSettings(action.settingType).fold({ it }, { it.localizedMessage ?: "Failed to open settings." })

                is PendingSensitiveAction.ShareTextAction ->
                    deviceManager.shareText("Shared from JARVIS", action.text).fold({ it }, { it.localizedMessage ?: "Failed to share." })

                is PendingSensitiveAction.SaveMemoryAction -> {
                    val res = repository.addMemory(
                        PersonalMemory(
                            userId = currentUserId,
                            keyLabel = action.keyLabel,
                            fact = action.fact,
                            category = action.category,
                            approved = true
                        )
                    )
                    if (res.isSuccess) "Memory saved: ${action.keyLabel}" else "Failed to save memory."
                }

                is PendingSensitiveAction.RunRoutineAction -> {
                    executeRoutineSteps(action.routine)
                    "Routine '${action.routine.name}' executed."
                }
            }
            _statusBanner.value = message
        }
    }

    fun dismissPendingAction() {
        _pendingAction.value = null
        _statusBanner.value = "Action cancelled by user."
    }

    // --- Productivity CRUD & AI Organization ---

    fun addTask(title: String, details: String, category: TaskCategory, priority: TaskPriority, dueLabel: String) {
        viewModelScope.launch {
            val res = repository.addTask(
                ProductivityTask(
                    userId = currentUserId,
                    title = title,
                    details = details,
                    category = category.name,
                    priority = priority.name,
                    completed = false,
                    dueLabel = dueLabel
                )
            )
            if (res.isFailure) {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to create task."
            }
        }
    }

    fun toggleTask(task: ProductivityTask) {
        viewModelScope.launch {
            val res = repository.toggleTaskCompletion(task)
            if (res.isFailure) {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to update task."
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTask(taskId)
        }
    }

    fun addNote(title: String, content: String, tag: String) {
        viewModelScope.launch {
            val res = repository.addNote(
                JarvisNote(userId = currentUserId, title = title, content = content, tag = tag)
            )
            if (res.isSuccess) {
                _statusBanner.value = "Saved note \"$title\"."
            } else {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to save note."
            }
        }
    }

    fun toggleNotePin(note: JarvisNote) {
        viewModelScope.launch {
            repository.toggleNotePin(note)
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
        }
    }

    fun organizeDayWithAi() {
        viewModelScope.launch {
            _dailyPlanSummary.value = UiState.Loading
            try {
                val tasks = (tasksState.value as? UiState.Success)?.data.orEmpty()
                val lang = PreferredLanguage.fromRaw(userProfile.value.preferredLanguage)
                val summary = aiEngine.organizeDailyPlan(tasks, lang)
                _dailyPlanSummary.value = UiState.Success(summary)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _dailyPlanSummary.value = UiState.Error(e.localizedMessage ?: "Failed to organize day.")
            }
        }
    }

    // --- Smart Routines ---

    fun addRoutine(name: String, triggerPhrase: String, type: RoutineType, steps: List<String>, requiresConfirmation: Boolean) {
        viewModelScope.launch {
            val res = repository.addRoutine(
                SmartRoutine(
                    userId = currentUserId,
                    name = name,
                    triggerPhrase = triggerPhrase,
                    routineType = type.name,
                    actionSteps = steps,
                    enabled = true,
                    requiresConfirmation = requiresConfirmation
                )
            )
            if (res.isFailure) {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to create routine."
            }
        }
    }

    fun toggleRoutine(routine: SmartRoutine) {
        viewModelScope.launch {
            repository.toggleRoutineEnabled(routine)
        }
    }

    fun deleteRoutine(routineId: String) {
        viewModelScope.launch {
            repository.deleteRoutine(routineId)
        }
    }

    fun triggerRoutine(routine: SmartRoutine) {
        if (routine.requiresConfirmation || userProfile.value.confirmSensitiveActions) {
            val preview = routine.actionSteps.joinToString("\n• ", prefix = "This routine will:\n• ")
            _pendingAction.value = PendingSensitiveAction.RunRoutineAction(routine = routine, description = preview)
        } else {
            executeRoutineSteps(routine)
        }
    }

    private fun executeRoutineSteps(routine: SmartRoutine) {
        val summaryPrompt = "Execute my ${routine.name} (${routine.routineType}). Steps requested: ${routine.actionSteps.joinToString("; ")}. Give a concise briefing and execute allowed planning actions."
        _requestedTab.value = JarvisTab.CORE
        sendUserMessage(summaryPrompt)
    }

    // --- Study Assistant ---

    fun generateStudyMaterial(subject: String, examTrack: ExamTrack, topic: String, resourceType: String) {
        if (topic.isBlank()) {
            _statusBanner.value = "Please enter a chapter or topic."
            return
        }
        viewModelScope.launch {
            _studyOutput.value = UiState.Loading
            try {
                val lang = PreferredLanguage.fromRaw(userProfile.value.preferredLanguage)
                val text = aiEngine.generateStudyResource(subject, examTrack, topic, resourceType, lang)
                _studyOutput.value = UiState.Success(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _studyOutput.value = UiState.Error(e.localizedMessage ?: "Failed to generate study material.")
            }
        }
    }

    fun generateMcqQuiz(subject: String, examTrack: ExamTrack, topic: String) {
        if (topic.isBlank()) {
            _statusBanner.value = "Please enter a topic for MCQ practice."
            return
        }
        viewModelScope.launch {
            _quizState.value = UiState.Loading
            try {
                val lang = PreferredLanguage.fromRaw(userProfile.value.preferredLanguage)
                val questions = aiEngine.generateStudyMcqQuiz(subject, examTrack, topic, lang)
                _quizState.value = UiState.Success(questions)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _quizState.value = UiState.Error(e.localizedMessage ?: "Failed to generate quiz.")
            }
        }
    }

    fun saveStudyAttempt(subject: String, examTrack: ExamTrack, topic: String, solved: Int, correct: Int, summary: String) {
        viewModelScope.launch {
            val mastery = if (solved > 0) ((correct * 100L) / solved).coerceIn(0L, 100L) else 50L
            val res = repository.recordStudySession(
                StudyProgress(
                    userId = currentUserId,
                    subject = subject,
                    topic = topic,
                    examTrack = examTrack.name,
                    masteryPercent = mastery,
                    questionsSolved = solved.toLong(),
                    correctCount = correct.toLong(),
                    notesSummary = summary
                )
            )
            if (res.isSuccess) {
                _statusBanner.value = "Study session logged ($correct/$solved correct)."
            }
        }
    }

    // --- Research Assistant ---

    fun runWebResearch(query: String, webpageUrl: String?) {
        if (query.isBlank() && webpageUrl.isNullOrBlank()) {
            _statusBanner.value = "Enter a research query or webpage URL."
            return
        }
        viewModelScope.launch {
            _researchState.value = UiState.Loading
            try {
                val lang = PreferredLanguage.fromRaw(userProfile.value.preferredLanguage)
                val effectiveQuery = query.ifBlank { "Summarize the key points and verify facts from this webpage." }
                val result = aiEngine.researchWithSearch(effectiveQuery, webpageUrl?.takeIf { it.isNotBlank() }, lang)
                _researchState.value = UiState.Success(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _researchState.value = UiState.Error(e.localizedMessage ?: "Research query failed.")
            }
        }
    }

    // --- Personal Memory, Conversation Summary & Settings ---

    fun addMemory(keyLabel: String, fact: String, category: MemoryCategory) {
        viewModelScope.launch {
            val res = repository.addMemory(
                PersonalMemory(
                    userId = currentUserId,
                    keyLabel = keyLabel,
                    fact = fact,
                    category = category.name,
                    approved = true
                )
            )
            if (res.isFailure) {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to save memory."
            }
        }
    }

    fun toggleMemoryApproved(memory: PersonalMemory) {
        viewModelScope.launch {
            repository.updateMemory(memory.id, memory.keyLabel, memory.fact, !memory.approved)
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            repository.deleteMemory(memoryId)
        }
    }

    fun updateSettings(updates: Map<String, Any>) {
        viewModelScope.launch {
            val res = repository.updateProfileSettings(updates)
            if (res.isFailure) {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to save settings."
            }
        }
    }

    fun selectTtsVoice(voiceName: String?) {
        voiceController.selectVoice(voiceName)
    }

    fun summarizeCurrentChatToNote() {
        viewModelScope.launch {
            _aiStatus.value = JarvisAiStatus.PROCESSING
            try {
                val msgs = (messagesState.value as? UiState.Success)?.data.orEmpty()
                val lang = PreferredLanguage.fromRaw(userProfile.value.preferredLanguage)
                val summary = aiEngine.summarizeConversation(msgs, lang)
                repository.addNote(
                    JarvisNote(
                        userId = currentUserId,
                        title = "JARVIS Conversation Summary",
                        content = summary,
                        tag = "Summary"
                    )
                )
                _statusBanner.value = "Conversation summary saved to Notes."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusBanner.value = e.localizedMessage ?: "Failed to summarize chat."
            } finally {
                _aiStatus.value = JarvisAiStatus.SYSTEM_ONLINE
            }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            val res = repository.clearConversations()
            _statusBanner.value = if (res.isSuccess) "Conversation history cleared." else "Failed to clear history."
        }
    }

    // --- Direct Android Intents ---

    fun getInstalledApps(): List<InstalledAppInfo> = deviceManager.findInstalledApps()

    fun launchInstalledApp(app: InstalledAppInfo) {
        if (userProfile.value.confirmSensitiveActions) {
            _pendingAction.value = PendingSensitiveAction.OpenAppAction(app.label, app.packageName)
        } else {
            deviceManager.launchApp(app.packageName)
        }
    }

    fun openExternalUrl(url: String) {
        if (userProfile.value.confirmSensitiveActions) {
            _pendingAction.value = PendingSensitiveAction.OpenUrlAction(url)
        } else {
            deviceManager.openWebsite(url)
        }
    }

    fun shareContent(title: String, body: String) {
        deviceManager.shareText(title, body)
    }

    fun openAppPermissionSettings() {
        deviceManager.openSettings("permissions")
    }

    fun openDeviceSettings(settingType: String) {
        if (userProfile.value.confirmSensitiveActions) {
            _pendingAction.value = PendingSensitiveAction.OpenSettingsAction(settingType)
        } else {
            deviceManager.openSettings(settingType)
        }
    }

    private fun unwrapFirestoreOrThrow(error: Throwable): String {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w(TAG, "Firestore stream error", root)
        return root.message ?: "Unable to sync data from Firestore."
    }

    override fun onCleared() {
        super.onCleared()
        voiceController.release()
    }
}
