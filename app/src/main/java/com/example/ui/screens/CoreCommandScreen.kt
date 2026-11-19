package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.AssistantMode
import com.example.core.JarvisAiStatus
import com.example.core.UiState
import com.example.model.ChatMessage
import com.example.ui.components.ArcReactorCore
import com.example.ui.components.GlassHudCard
import com.example.ui.components.VoiceWaveformBar
import com.example.ui.theme.GlassCardBg
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusRed
import com.example.viewmodel.JarvisTab
import com.example.viewmodel.JarvisViewModel

@Composable
fun CoreCommandScreen(
    viewModel: JarvisViewModel,
    onNavigateToTab: (JarvisTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val aiStatus by viewModel.aiStatus.collectAsStateWithLifecycle()
    val assistantMode by viewModel.assistantMode.collectAsStateWithLifecycle()
    val speakerEnabled by viewModel.speakerEnabled.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val rmsDb by viewModel.rmsDb.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val telemetry by viewModel.deviceTelemetry.collectAsStateWithLifecycle()
    val attachedBitmap by viewModel.attachedBitmap.collectAsStateWithLifecycle()
    val attachedDocName by viewModel.attachedDocName.collectAsStateWithLifecycle()

    var inputText by rememberSaveable { mutableStateOf("") }
    var micPermissionDeniedBanner by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.refreshDeviceTelemetry()
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        micPermissionDeniedBanner = false
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    if (isListening) {
                        viewModel.cancelVoiceListening()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val systemSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val transcript = matches?.firstOrNull().orEmpty().trim()
            if (transcript.isNotEmpty()) {
                viewModel.onVoiceTranscript(transcript)
            }
        }
    }

    fun startSpeechCapture() {
        if (viewModel.isSpeechRecognitionAvailable()) {
            viewModel.startVoiceListening()
        } else if (viewModel.canLaunchSystemSpeechIntent()) {
            systemSpeechLauncher.launch(viewModel.createSystemSpeechIntent())
        } else {
            viewModel.startVoiceListening()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshDeviceTelemetry()
        if (granted) {
            micPermissionDeniedBanner = false
            startSpeechCapture()
        } else {
            micPermissionDeniedBanner = true
            val activity = context.findActivity()
            val showRationale = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.RECORD_AUDIO
            )
            viewModel.onMicrophonePermissionDenied(permanentlyDenied = !showRationale)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            viewModel.attachBitmap(bitmap, "Camera Snapshot")
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        viewModel.attachBitmap(bmp, "Selected Image")
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val mime = context.contentResolver.getType(uri) ?: "application/pdf"
                var fileName = "Document"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIdx >= 0) {
                        fileName = cursor.getString(nameIdx)
                    }
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bytes = stream.readBytes()
                    viewModel.attachDocument(bytes, mime, fileName)
                }
            } catch (_: Exception) {
            }
        }
    }

    fun triggerVoiceButton() {
        if (isSpeaking) {
            viewModel.interruptSpeech()
        }
        if (isListening) {
            viewModel.stopVoiceListening()
        } else {
            val hasMic = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasMic) {
                micPermissionDeniedBanner = false
                startSpeechCapture()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Top HUD Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "J.A.R.V.I.S.",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.BatteryChargingFull,
                            contentDescription = "Battery telemetry",
                            tint = StatusEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${if (telemetry.batteryPercent >= 0) "${telemetry.batteryPercent}%" else "--"} • ${telemetry.networkStatus} • ${profile.preferredLanguage}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.summarizeCurrentChatToNote() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("summarize_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Summarize,
                            contentDescription = "Summarize conversation to Notes",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { viewModel.toggleSpeaker() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("speaker_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (speakerEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            contentDescription = if (speakerEnabled) "Mute JARVIS voice" else "Enable JARVIS voice",
                            tint = if (speakerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onNavigateToTab(JarvisTab.SETTINGS) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("header_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Open JARVIS Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Assistant Mode Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistantMode.entries.forEach { mode ->
                    FilterChip(
                        selected = assistantMode == mode,
                        onClick = { viewModel.setAssistantMode(mode) },
                        label = {
                            Text(mode.label, style = MaterialTheme.typography.labelMedium)
                        },
                        modifier = Modifier.testTag("mode_chip_${mode.name.lowercase()}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // Animated Central AI Core
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                ArcReactorCore(
                    status = aiStatus,
                    rmsDb = rmsDb,
                    onCoreTap = { triggerVoiceButton() },
                    coreSize = 152.dp
                )
            }

            // Live Voice Partial Transcript & Waveform HUD
            AnimatedVisibility(visible = isListening || partialTranscript.isNotBlank()) {
                GlassHudCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .testTag("live_voice_hud_card"),
                    accentBorder = StatusEmerald.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            VoiceWaveformBar(
                                rmsDb = rmsDb,
                                barColor = StatusEmerald
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "MIC ACTIVE • ${profile.preferredLanguage.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusEmerald
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (partialTranscript.isNotBlank()) {
                                        "\"$partialTranscript\""
                                    } else {
                                        "Speak your request in Hindi, Hinglish, or English..."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (partialTranscript.isNotBlank()) {
                                IconButton(
                                    onClick = { viewModel.submitPartialTranscriptNow() },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("send_voice_transcript_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send spoken transcript",
                                        tint = StatusEmerald
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.cancelVoiceListening() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("cancel_voice_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Cancel voice input",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Microphone Permission Guidance Card (when denied)
            AnimatedVisibility(visible = micPermissionDeniedBanner) {
                GlassHudCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("mic_permission_card"),
                    accentBorder = StatusRed.copy(alpha = 0.65f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Microphone access is required for voice commands.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        AssistChip(
                            onClick = { viewModel.openAppPermissionSettings() },
                            label = { Text("Settings") },
                            modifier = Modifier.testTag("open_mic_settings_button")
                        )
                    }
                }
            }

            // Quick Action Shortcuts Rail
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                item {
                    QuickActionChip("Daily Plan") {
                        viewModel.sendUserMessage("Organize my pending tasks and give me a daily briefing.")
                    }
                }
                item {
                    QuickActionChip("Study Mode") {
                        onNavigateToTab(JarvisTab.STUDY)
                    }
                }
                item {
                    QuickActionChip("Web Research") {
                        onNavigateToTab(JarvisTab.RESEARCH)
                    }
                }
                item {
                    QuickActionChip("Remind 7 PM") {
                        viewModel.sendUserMessage("Remind me to study at 7 PM today.")
                    }
                }
                item {
                    QuickActionChip("Device Status") {
                        viewModel.sendUserMessage("Check my current battery and network status.")
                    }
                }
                item {
                    QuickActionChip("Newton's Laws") {
                        viewModel.sendUserMessage("Explain Newton's three laws in simple Hinglish with examples.")
                    }
                }
            }

            // Conversation History
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (messagesState) {
                    is UiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    is UiState.Error -> {
                        GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = (messagesState as UiState.Error).message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    is UiState.Success -> {
                        if (messages.isEmpty()) {
                            GlassHudCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "SYSTEM INITIALIZED, ${profile.displayName.uppercase()}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Ask me anything in Hindi, Hinglish, or English. Tap the reactor or mic to speak, attach an image/PDF, or try:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                listOf(
                                    "\"Hey JARVIS, how are you?\"",
                                    "\"JARVIS, remind me to revise Electrostatics at 7 PM\"",
                                    "\"Newton's laws Hinglish mein samjhao\""
                                ).forEach { sample ->
                                    Text(
                                        text = sample,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clickable { viewModel.sendUserMessage(sample.trim('"')) }
                                            .padding(vertical = 4.dp)
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(vertical = 6.dp)
                            ) {
                                items(messages, key = { it.id.ifEmpty { it.hashCode().toString() } }) { msg ->
                                    ChatBubbleCard(
                                        message = msg,
                                        onSpeak = { viewModel.speakMessage(msg.text) },
                                        onShare = { viewModel.shareContent("JARVIS Response", msg.text) },
                                        onSaveNote = {
                                            viewModel.addNote(
                                                title = msg.text.lines().firstOrNull()?.take(48) ?: "JARVIS Insight",
                                                content = msg.text,
                                                tag = msg.mode
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Attachment Preview Chip
            AnimatedVisibility(visible = attachedDocName != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (attachedBitmap != null) {
                            Image(
                                bitmap = attachedBitmap!!.asImageBitmap(),
                                contentDescription = "Attached preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            text = attachedDocName.orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearAttachment() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove attachment",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Bottom Command Input Dock
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { cameraLauncher.launch(null) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("camera_attach_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "Capture photo for JARVIS",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("photo_attach_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = "Pick photo for JARVIS",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = {
                        docPickerLauncher.launch(arrayOf("application/pdf", "text/plain"))
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("doc_attach_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AttachFile,
                        contentDescription = "Attach PDF or text document",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            "Command JARVIS...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank() || attachedDocName != null) {
                                val prompt = inputText
                                inputText = ""
                                viewModel.sendUserMessage(prompt)
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = GlassCardBg,
                        unfocusedContainerColor = GlassCardBg
                    ),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("command_text_input")
                )

                if (inputText.isNotBlank() || attachedDocName != null) {
                    IconButton(
                        onClick = {
                            val prompt = inputText
                            inputText = ""
                            viewModel.sendUserMessage(prompt)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("send_command_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send command",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                } else {
                    val isBusyVoice = isListening || isSpeaking
                    IconButton(
                        onClick = { triggerVoiceButton() },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(
                                if (isListening) StatusEmerald else MaterialTheme.colorScheme.primary
                            )
                            .border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                            .testTag("mic_command_button")
                    ) {
                        Icon(
                            imageVector = if (isBusyVoice) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (isListening) "Stop listening" else "Speak to JARVIS",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionChip(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = GlassCardBg,
            labelColor = MaterialTheme.colorScheme.primary
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = true,
            borderColor = MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
private fun ChatBubbleCard(
    message: ChatMessage,
    onSpeak: () -> Unit,
    onShare: () -> Unit,
    onSaveNote: () -> Unit
) {
    val isUser = message.role == "user"
    val border = if (isUser) {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        GlassHudCard(
            modifier = Modifier.widthIn(max = 560.dp),
            accentBorder = border
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isUser) "YOU" else "J.A.R.V.I.S. • ${message.mode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
                if (!isUser) {
                    Row {
                        IconButton(onClick = onSpeak, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Filled.VolumeUp,
                                contentDescription = "Read response aloud",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(onClick = onSaveNote, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Filled.BookmarkAdd,
                                contentDescription = "Save response to notes",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share response",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            if (!message.attachmentLabel.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "[Attachment: ${message.attachmentLabel}]",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!message.actionSummary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Action: ${message.actionSummary}",
                    style = MaterialTheme.typography.labelMedium,
                    color = StatusEmerald
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
