package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.configureAppCheck
import com.example.data.JarvisAiEngine
import com.example.data.JarvisFirestoreRepository
import com.example.data.authStateFlow
import com.example.model.HudTheme
import com.example.platform.DeviceIntegrationManager
import com.example.platform.VoiceController
import com.example.ui.components.JarvisHudBackdrop
import com.example.ui.components.SensitiveActionConfirmationDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CoreCommandScreen
import com.example.ui.screens.ProductivityScreen
import com.example.ui.screens.ResearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudyScreen
import com.example.ui.theme.GlassCardBg
import com.example.ui.theme.JarvisTheme
import com.example.viewmodel.JarvisTab
import com.example.viewmodel.JarvisViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before any UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            JarvisAppNavigation()
        }
    }
}

@Composable
fun JarvisAppNavigation() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        JarvisTheme(hudTheme = HudTheme.ARC_CYAN) {
            AuthScreen(onAuthSuccess = {})
        }
    } else {
        val uid = user.uid
        key(uid) {
            JarvisAuthenticatedShell(
                currentUserId = uid,
                displayName = user.displayName
            )
        }
    }
}

@Composable
private fun JarvisAuthenticatedShell(
    currentUserId: String,
    displayName: String?
) {
    val appContext = LocalContext.current.applicationContext
    val viewModel: JarvisViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val repository = JarvisFirestoreRepository(db)
                val aiEngine = JarvisAiEngine()
                val deviceManager = DeviceIntegrationManager(app)

                lateinit var vmRef: JarvisViewModel
                val voiceController = VoiceController(
                    context = app,
                    onTranscriptReady = { transcript -> vmRef.onVoiceTranscript(transcript) },
                    onSpeechFinished = { vmRef.onSpeechFinished() }
                )
                vmRef = JarvisViewModel(
                    repository = repository,
                    aiEngine = aiEngine,
                    voiceController = voiceController,
                    deviceManager = deviceManager,
                    currentUserId = currentUserId,
                    initialDisplayName = displayName
                )
                vmRef
            }
        }
    )

    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val pendingAction by viewModel.pendingAction.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val voiceError by viewModel.voiceError.collectAsStateWithLifecycle()
    val requestedTab by viewModel.requestedTab.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(JarvisTab.CORE) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(requestedTab) {
        requestedTab?.let { target ->
            currentTab = target
            viewModel.consumeRequestedTab()
        }
    }

    LaunchedEffect(statusBanner, voiceError) {
        val msg = statusBanner ?: voiceError
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    BackHandler(enabled = currentTab != JarvisTab.CORE) {
        currentTab = JarvisTab.CORE
    }

    val activeTheme = remember(profile.hudTheme) {
        HudTheme.fromRaw(profile.hudTheme)
    }

    JarvisTheme(hudTheme = activeTheme) {
        JarvisHudBackdrop(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth >= 600.dp

                Scaffold(
                    containerColor = Color.Transparent,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (!isWideScreen) {
                            NavigationBar(
                                containerColor = GlassCardBg
                            ) {
                                JarvisTab.entries.forEach { tab ->
                                    val selected = currentTab == tab
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { currentTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = tabIcon(tab, selected),
                                                contentDescription = tab.title
                                            )
                                        },
                                        label = {
                                            Text(tab.title, style = MaterialTheme.typography.labelSmall)
                                        },
                                        modifier = Modifier.testTag("nav_tab_${tab.route}")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (isWideScreen) {
                            NavigationRail(
                                containerColor = GlassCardBg
                            ) {
                                JarvisTab.entries.forEach { tab ->
                                    val selected = currentTab == tab
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { currentTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = tabIcon(tab, selected),
                                                contentDescription = tab.title
                                            )
                                        },
                                        label = { Text(tab.title) },
                                        modifier = Modifier.testTag("rail_tab_${tab.route}")
                                    )
                                }
                            }
                        }

                        when (currentTab) {
                            JarvisTab.CORE -> CoreCommandScreen(
                                viewModel = viewModel,
                                onNavigateToTab = { currentTab = it },
                                modifier = Modifier.weight(1f)
                            )

                            JarvisTab.PRODUCTIVITY -> ProductivityScreen(
                                viewModel = viewModel,
                                modifier = Modifier.weight(1f)
                            )

                            JarvisTab.STUDY -> StudyScreen(
                                viewModel = viewModel,
                                onNavigateToTab = { currentTab = it },
                                modifier = Modifier.weight(1f)
                            )

                            JarvisTab.RESEARCH -> ResearchScreen(
                                viewModel = viewModel,
                                modifier = Modifier.weight(1f)
                            )

                            JarvisTab.SETTINGS -> SettingsScreen(
                                viewModel = viewModel,
                                onSignedOut = {},
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                pendingAction?.let { action ->
                    SensitiveActionConfirmationDialog(
                        action = action,
                        onConfirm = { viewModel.confirmPendingAction() },
                        onDismiss = { viewModel.dismissPendingAction() }
                    )
                }
            }
        }
    }
}

private fun tabIcon(tab: JarvisTab, selected: Boolean) = when (tab) {
    JarvisTab.CORE -> if (selected) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome
    JarvisTab.PRODUCTIVITY -> if (selected) Icons.Filled.Checklist else Icons.Outlined.Checklist
    JarvisTab.STUDY -> if (selected) Icons.Filled.School else Icons.Outlined.School
    JarvisTab.RESEARCH -> if (selected) Icons.Filled.Public else Icons.Outlined.Public
    JarvisTab.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
}
