package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.UiState
import com.example.data.signOut
import com.example.model.HudTheme
import com.example.model.MemoryCategory
import com.example.model.PreferredLanguage
import com.example.ui.components.GlassHudCard
import com.example.ui.components.SectionHeaderRow
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusRed
import com.example.viewmodel.JarvisViewModel

@Composable
fun SettingsScreen(
    viewModel: JarvisViewModel,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val memoriesState by viewModel.memoriesState.collectAsStateWithLifecycle()
    val availableVoices by viewModel.availableVoices.collectAsStateWithLifecycle()
    val selectedVoice by viewModel.selectedVoiceName.collectAsStateWithLifecycle()
    val permissionStatus by viewModel.permissionStatus.collectAsStateWithLifecycle()

    var instructionsDraft by remember(profile.customInstructions) {
        mutableStateOf(profile.customInstructions)
    }
    var speedDraft by remember(profile.speechSpeed) { mutableFloatStateOf(profile.speechSpeed.toFloat()) }
    var pitchDraft by remember(profile.speechPitch) { mutableFloatStateOf(profile.speechPitch.toFloat()) }

    var showAddMemoryDialog by remember { mutableStateOf(false) }
    var showClearChatConfirm by remember { mutableStateOf(false) }
    var showAppsSheet by remember { mutableStateOf(false) }
    var appSearch by remember { mutableStateOf("") }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshDeviceTelemetry()
        viewModel.updateSettings(mapOf("notificationsEnabled" to granted))
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                SectionHeaderRow(
                    title = "SETTINGS & MEMORY",
                    subtitle = "Voice • Language • Theme • Memory • Privacy"
                )
            }

            // Voice & Language
            item {
                GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                    Text("LANGUAGE & VOICE ENGINE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PreferredLanguage.entries) { lang ->
                            FilterChip(
                                selected = profile.preferredLanguage == lang.name,
                                onClick = { viewModel.updateSettings(mapOf("preferredLanguage" to lang.name)) },
                                label = { Text(lang.label) },
                                modifier = Modifier.testTag("lang_chip_${lang.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Speech Speed: ${"%.2fx".format(speedDraft)}", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = speedDraft,
                        onValueChange = { speedDraft = it },
                        onValueChangeFinished = {
                            viewModel.updateSettings(mapOf("speechSpeed" to speedDraft.toDouble()))
                        },
                        valueRange = 0.5f..2.0f
                    )

                    Text("Voice Pitch: ${"%.2fx".format(pitchDraft)}", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = pitchDraft,
                        onValueChange = { pitchDraft = it },
                        onValueChangeFinished = {
                            viewModel.updateSettings(mapOf("speechPitch" to pitchDraft.toDouble()))
                        },
                        valueRange = 0.6f..1.6f
                    )

                    if (availableVoices.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("TTS Voice Selection", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(availableVoices) { v ->
                                FilterChip(
                                    selected = selectedVoice == v.name,
                                    onClick = { viewModel.selectTtsVoice(v.name) },
                                    label = { Text(v.localeDisplay) }
                                )
                            }
                        }
                    }

                    SettingToggleRow(
                        title = "Continuous Conversation Mode",
                        subtitle = "Automatically listen again after JARVIS finishes speaking",
                        checked = profile.continuousVoice,
                        onCheckedChange = { viewModel.updateSettings(mapOf("continuousVoice" to it)) }
                    )
                    SettingToggleRow(
                        title = "Wake-Word Prefix (\"Hey JARVIS\")",
                        subtitle = "Strip wake-word prefix from voice transcripts",
                        checked = profile.wakeWordEnabled,
                        onCheckedChange = { viewModel.updateSettings(mapOf("wakeWordEnabled" to it)) }
                    )
                }
            }

            // HUD Theme Selection
            item {
                GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                    Text("HUD APPEARANCE THEME", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(HudTheme.entries) { theme ->
                            FilterChip(
                                selected = profile.hudTheme == theme.name,
                                onClick = { viewModel.updateSettings(mapOf("hudTheme" to theme.name)) },
                                label = { Text(theme.title) },
                                modifier = Modifier.testTag("theme_chip_${theme.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Personal Memory & Custom Instructions
            item {
                GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("PERSONAL MEMORY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        OutlinedButton(onClick = { showAddMemoryDialog = true }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Memory")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = instructionsDraft,
                        onValueChange = { instructionsDraft = it },
                        label = { Text("Custom Instructions for JARVIS") },
                        minLines = 2,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            viewModel.updateSettings(mapOf("customInstructions" to instructionsDraft.take(1000)))
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save Instructions")
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    when (val memState = memoriesState) {
                        is UiState.Loading -> Text("Loading memories...", style = MaterialTheme.typography.bodyMedium)
                        is UiState.Error -> Text(memState.message, color = MaterialTheme.colorScheme.error)
                        is UiState.Success -> {
                            if (memState.data.isEmpty()) {
                                Text(
                                    text = "No personal memories saved yet. JARVIS never stores passwords or sensitive credentials.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                memState.data.forEach { mem ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${mem.keyLabel} [${mem.category}]", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                            Text(mem.fact, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        Switch(
                                            checked = mem.approved,
                                            onCheckedChange = { viewModel.toggleMemoryApproved(mem) }
                                        )
                                        IconButton(onClick = { viewModel.deleteMemory(mem.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete memory")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Android Phone Integrations
            item {
                GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                    Text("ANDROID PHONE INTEGRATIONS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { OutlinedButton(onClick = { viewModel.openDeviceSettings("wifi") }) { Text("Wi-Fi") } }
                        item { OutlinedButton(onClick = { viewModel.openDeviceSettings("bluetooth") }) { Text("Bluetooth") } }
                        item { OutlinedButton(onClick = { viewModel.openDeviceSettings("display") }) { Text("Display") } }
                        item { OutlinedButton(onClick = { viewModel.openDeviceSettings("battery") }) { Text("Battery") } }
                        item {
                            Button(onClick = { showAppsSheet = true }) {
                                Icon(Icons.Filled.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Installed Apps")
                            }
                        }
                    }
                }
            }

            // Security, Privacy & Permissions
            item {
                GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SECURITY, PRIVACY & PERMISSIONS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    SettingToggleRow(
                        title = "Confirm Sensitive Actions",
                        subtitle = "Require explicit tap before opening apps, websites, settings, alarms, or routines",
                        checked = profile.confirmSensitiveActions,
                        onCheckedChange = { viewModel.updateSettings(mapOf("confirmSensitiveActions" to it)) }
                    )
                    SettingToggleRow(
                        title = "Auto-Save Approved Memories",
                        subtitle = "Allow JARVIS to remember preferences when confirmation is disabled",
                        checked = profile.autoSaveMemories,
                        onCheckedChange = { viewModel.updateSettings(mapOf("autoSaveMemories" to it)) }
                    )
                    SettingToggleRow(
                        title = "Reminder Notifications",
                        subtitle = "Allow system notifications for reminders",
                        checked = profile.notificationsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissionStatus.notificationsGranted) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.updateSettings(mapOf("notificationsEnabled" to enabled))
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Microphone: ${if (permissionStatus.microphoneGranted) "GRANTED" else "DENIED"} • Notifications: ${if (permissionStatus.notificationsGranted) "GRANTED" else "DENIED"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (permissionStatus.microphoneGranted) StatusEmerald else StatusRed
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.openAppPermissionSettings() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Manage / Revoke")
                        }
                        OutlinedButton(
                            onClick = { showClearChatConfirm = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear Chat")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            signOut(credentialManager, scope, onSignOutComplete = onSignedOut)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }
            }

            // Gemini AI Engine & About JARVIS
            item {
                GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                    Text("GEMINI AI & ABOUT JARVIS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "J.A.R.V.I.S. — Just A Rather Very Intelligent System (v1.0)\n" +
                            "AI Engine: Gemini 3.8 Flash via Firebase AI Logic with App Check attestation (no client-side API keys exposed).\n" +
                            "Data Storage: User-isolated Cloud Firestore with Google Sign-In.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showAddMemoryDialog) {
        var keyLabel by remember { mutableStateOf("") }
        var fact by remember { mutableStateOf("") }
        var category by remember { mutableStateOf(MemoryCategory.PREFERENCE) }
        AlertDialog(
            onDismissRequest = { showAddMemoryDialog = false },
            title = { Text("Add Personal Memory") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = keyLabel, onValueChange = { keyLabel = it }, label = { Text("Label (e.g., Study Goal)") }, singleLine = true)
                    OutlinedTextField(value = fact, onValueChange = { fact = it }, label = { Text("What should JARVIS remember?") })
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(MemoryCategory.entries) { c ->
                            FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.name) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addMemory(keyLabel, fact, category)
                        showAddMemoryDialog = false
                    },
                    enabled = keyLabel.isNotBlank() && fact.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { OutlinedButton(onClick = { showAddMemoryDialog = false }) { Text("Cancel") } }
        )
    }

    if (showClearChatConfirm) {
        AlertDialog(
            onDismissRequest = { showClearChatConfirm = false },
            title = { Text("Clear Conversation History?") },
            text = { Text("This permanently deletes your cloud-synced JARVIS chat history. Personal memories and notes remain safe.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearChatHistory()
                        showClearChatConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete History") }
            },
            dismissButton = { OutlinedButton(onClick = { showClearChatConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showAppsSheet) {
        val apps = remember(appSearch) {
            viewModel.getInstalledApps().filter {
                appSearch.isBlank() || it.label.contains(appSearch, ignoreCase = true)
            }.take(25)
        }
        AlertDialog(
            onDismissRequest = { showAppsSheet = false },
            title = { Text("Launch Installed App") },
            text = {
                Column {
                    OutlinedTextField(
                        value = appSearch,
                        onValueChange = { appSearch = it },
                        label = { Text("Filter apps") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.height(260.dp)) {
                        items(apps, key = { it.packageName }) { app ->
                            OutlinedButton(
                                onClick = {
                                    showAppsSheet = false
                                    viewModel.launchInstalledApp(app)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(app.label, modifier = Modifier.weight(1f))
                                Icon(Icons.Filled.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showAppsSheet = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
