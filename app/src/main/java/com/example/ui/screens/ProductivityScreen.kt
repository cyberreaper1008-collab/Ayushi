package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.UiState
import com.example.model.JarvisNote
import com.example.model.ProductivityTask
import com.example.model.RoutineType
import com.example.model.SmartRoutine
import com.example.model.TaskCategory
import com.example.model.TaskPriority
import com.example.ui.components.GlassHudCard
import com.example.ui.components.SectionHeaderRow
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusRed
import com.example.viewmodel.JarvisViewModel

@Composable
fun ProductivityScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val notesState by viewModel.notesState.collectAsStateWithLifecycle()
    val routinesState by viewModel.routinesState.collectAsStateWithLifecycle()
    val dailyPlanSummary by viewModel.dailyPlanSummary.collectAsStateWithLifecycle()

    var sectionIndex by rememberSaveable { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }

    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var showAddRoutineDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            SectionHeaderRow(
                title = "PRODUCTIVITY & AUTOMATION",
                subtitle = "Planner • Notes • Habits • Smart Routines"
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Tasks & Habits", "Notes", "Routines").forEachIndexed { idx, label ->
                    FilterChip(
                        selected = sectionIndex == idx,
                        onClick = { sectionIndex = idx },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.testTag("productivity_tab_$idx")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                placeholder = { Text("Search tasks, notes, or routines...") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("productivity_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            when (sectionIndex) {
                0 -> TasksSection(
                    tasksState = tasksState,
                    dailyPlanSummary = dailyPlanSummary,
                    searchQuery = searchQuery,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { selectedCategory = it },
                    onOrganizeDay = { viewModel.organizeDayWithAi() },
                    onToggleTask = { viewModel.toggleTask(it) },
                    onDeleteTask = { viewModel.deleteTask(it) },
                    modifier = Modifier.weight(1f)
                )

                1 -> NotesSection(
                    notesState = notesState,
                    searchQuery = searchQuery,
                    onTogglePin = { viewModel.toggleNotePin(it) },
                    onShareNote = { viewModel.shareContent(it.title, it.content) },
                    onDeleteNote = { viewModel.deleteNote(it) },
                    modifier = Modifier.weight(1f)
                )

                else -> RoutinesSection(
                    routinesState = routinesState,
                    searchQuery = searchQuery,
                    onCreateTemplate = { name, trigger, type, steps ->
                        viewModel.addRoutine(name, trigger, type, steps, requiresConfirmation = true)
                    },
                    onToggleRoutine = { viewModel.toggleRoutine(it) },
                    onRunRoutine = { viewModel.triggerRoutine(it) },
                    onDeleteRoutine = { viewModel.deleteRoutine(it) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        FloatingActionButton(
            onClick = {
                when (sectionIndex) {
                    0 -> showAddTaskDialog = true
                    1 -> showAddNoteDialog = true
                    else -> showAddRoutineDialog = true
                }
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("productivity_fab")
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Create item")
        }
    }

    if (showAddTaskDialog) {
        AddTaskDialog(
            onDismiss = { showAddTaskDialog = false },
            onSave = { title, details, cat, prio, due ->
                viewModel.addTask(title, details, cat, prio, due)
                showAddTaskDialog = false
            }
        )
    }

    if (showAddNoteDialog) {
        AddNoteDialog(
            onDismiss = { showAddNoteDialog = false },
            onSave = { title, content, tag ->
                viewModel.addNote(title, content, tag)
                showAddNoteDialog = false
            }
        )
    }

    if (showAddRoutineDialog) {
        AddRoutineDialog(
            onDismiss = { showAddRoutineDialog = false },
            onSave = { name, trigger, type, steps, confirm ->
                viewModel.addRoutine(name, trigger, type, steps, confirm)
                showAddRoutineDialog = false
            }
        )
    }
}

@Composable
private fun TasksSection(
    tasksState: UiState<List<ProductivityTask>>,
    dailyPlanSummary: UiState<String>?,
    searchQuery: String,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    onOrganizeDay: () -> Unit,
    onToggleTask: (ProductivityTask) -> Unit,
    onDeleteTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { onSelectCategory(null) },
                        label = { Text("All") }
                    )
                }
                items(TaskCategory.entries) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat.name,
                        onClick = { onSelectCategory(if (selectedCategory == cat.name) null else cat.name) },
                        label = { Text(cat.name) }
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onOrganizeDay,
                modifier = Modifier.testTag("ai_organize_day_button")
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("AI Plan")
            }
        }

        if (dailyPlanSummary != null) {
            Spacer(modifier = Modifier.height(8.dp))
            GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "JARVIS DAILY BRIEFING",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                when (dailyPlanSummary) {
                    is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    is UiState.Error -> Text(dailyPlanSummary.message, color = MaterialTheme.colorScheme.error)
                    is UiState.Success -> Text(dailyPlanSummary.data, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (tasksState) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is UiState.Error -> GlassHudCard(Modifier.fillMaxWidth()) {
                Text(tasksState.message, color = MaterialTheme.colorScheme.error)
            }

            is UiState.Success -> {
                val filtered = tasksState.data.filter { task ->
                    val matchesSearch = searchQuery.isBlank() ||
                        task.title.contains(searchQuery, ignoreCase = true) ||
                        task.details.contains(searchQuery, ignoreCase = true)
                    val matchesCat = selectedCategory == null || task.category == selectedCategory
                    matchesSearch && matchesCat
                }
                if (filtered.isEmpty()) {
                    GlassHudCard(Modifier.fillMaxWidth()) {
                        Text(
                            text = "No tasks or habits yet. Tap + or tell JARVIS: \"Remind me to revise Physics at 7 PM\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 84.dp)
                    ) {
                        items(filtered, key = { it.id }) { task ->
                            val priorityColor = when (TaskPriority.fromRaw(task.priority)) {
                                TaskPriority.HIGH -> StatusRed
                                TaskPriority.MEDIUM -> StatusAmber
                                TaskPriority.LOW -> StatusEmerald
                            }
                            GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = task.completed,
                                        onCheckedChange = { onToggleTask(task) }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = task.title,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (task.details.isNotBlank()) {
                                            Text(
                                                text = task.details,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = "${task.category} • ${task.priority}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = priorityColor
                                            )
                                            Text(
                                                text = "Due: ${task.dueLabel}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (task.category == TaskCategory.HABIT.name) {
                                                Text(
                                                    text = "Streak: ${task.streakCount}d",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = StatusEmerald
                                                )
                                            }
                                        }
                                    }
                                    IconButton(
                                        onClick = { onDeleteTask(task.id) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Delete task",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesSection(
    notesState: UiState<List<JarvisNote>>,
    searchQuery: String,
    onTogglePin: (JarvisNote) -> Unit,
    onShareNote: (JarvisNote) -> Unit,
    onDeleteNote: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (notesState) {
        is UiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is UiState.Error -> Box(modifier.fillMaxSize()) {
            GlassHudCard(Modifier.fillMaxWidth()) {
                Text(notesState.message, color = MaterialTheme.colorScheme.error)
            }
        }

        is UiState.Success -> {
            val filtered = notesState.data
                .filter {
                    searchQuery.isBlank() ||
                        it.title.contains(searchQuery, ignoreCase = true) ||
                        it.content.contains(searchQuery, ignoreCase = true)
                }
                .sortedByDescending { it.pinned }
            if (filtered.isEmpty()) {
                Box(modifier.fillMaxSize()) {
                    GlassHudCard(Modifier.fillMaxWidth()) {
                        Text(
                            text = "No saved notes yet. Tap + or ask JARVIS to save a note.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    items(filtered, key = { it.id }) { note ->
                        GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = note.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "#${note.tag}${if (note.pinned) " • PINNED" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onTogglePin(note) }, modifier = Modifier.size(48.dp)) {
                                    Icon(
                                        imageVector = Icons.Filled.PushPin,
                                        contentDescription = "Pin note",
                                        tint = if (note.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onShareNote(note) }, modifier = Modifier.size(48.dp)) {
                                    Icon(
                                        imageVector = Icons.Filled.Share,
                                        contentDescription = "Share note",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { onDeleteNote(note.id) }, modifier = Modifier.size(48.dp)) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete note",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = note.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutinesSection(
    routinesState: UiState<List<SmartRoutine>>,
    searchQuery: String,
    onCreateTemplate: (String, String, RoutineType, List<String>) -> Unit,
    onToggleRoutine: (SmartRoutine) -> Unit,
    onRunRoutine: (SmartRoutine) -> Unit,
    onDeleteRoutine: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // One-tap Routine Blueprint Templates
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedButton(onClick = {
                    onCreateTemplate(
                        "Morning Briefing",
                        "start my morning",
                        RoutineType.MORNING,
                        listOf("Read daily priorities", "Check battery & network status", "Summarize schedule")
                    )
                }) { Text("+ Morning") }
            }
            item {
                OutlinedButton(onClick = {
                    onCreateTemplate(
                        "Study Focus Session",
                        "study time",
                        RoutineType.STUDY,
                        listOf("Open Study Planner", "Review Physics & Math priorities", "Start 25m focus briefing")
                    )
                }) { Text("+ Study") }
            }
            item {
                OutlinedButton(onClick = {
                    onCreateTemplate(
                        "Night Wind-Down",
                        "good night jarvis",
                        RoutineType.NIGHT,
                        listOf("Summarize completed tasks", "Prepare tomorrow's top 3 priorities")
                    )
                }) { Text("+ Night") }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (routinesState) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is UiState.Error -> GlassHudCard(Modifier.fillMaxWidth()) {
                Text(routinesState.message, color = MaterialTheme.colorScheme.error)
            }

            is UiState.Success -> {
                val filtered = routinesState.data.filter {
                    searchQuery.isBlank() ||
                        it.name.contains(searchQuery, ignoreCase = true) ||
                        it.triggerPhrase.contains(searchQuery, ignoreCase = true)
                }
                if (filtered.isEmpty()) {
                    GlassHudCard(Modifier.fillMaxWidth()) {
                        Text(
                            text = "Create a Morning, Study, or Custom Voice Routine above. JARVIS always previews what each automation will do before enabling or running it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 84.dp)
                    ) {
                        items(filtered, key = { it.id }) { routine ->
                            GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = routine.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Voice Trigger: \"${routine.triggerPhrase}\" • ${routine.routineType}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = routine.enabled,
                                        onCheckedChange = { onToggleRoutine(routine) }
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Actions: ${routine.actionSteps.joinToString(" -> ")}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(onClick = { onRunRoutine(routine) }) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Preview & Run")
                                    }
                                    IconButton(onClick = { onDeleteRoutine(routine.id) }, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete routine")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddTaskDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, TaskCategory, TaskPriority, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var dueLabel by remember { mutableStateOf("Today") }
    var category by remember { mutableStateOf(TaskCategory.TASK) }
    var priority by remember { mutableStateOf(TaskPriority.MEDIUM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Planner Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(value = details, onValueChange = { details = it }, label = { Text("Details") })
                OutlinedTextField(value = dueLabel, onValueChange = { dueLabel = it }, label = { Text("Due / Time") }, singleLine = true)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(TaskCategory.entries) { cat ->
                        FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat.name) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TaskPriority.entries.forEach { p ->
                        FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p.name) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank()) onSave(title, details, category, priority, dueLabel) },
                enabled = title.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddNoteDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("General") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Content") }, minLines = 3)
                OutlinedTextField(value = tag, onValueChange = { tag = it }, label = { Text("Tag") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank() && content.isNotBlank()) onSave(title, content, tag) },
                enabled = title.isNotBlank() && content.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddRoutineDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, RoutineType, List<String>, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("") }
    var stepsRaw by remember { mutableStateOf("Check pending tasks, Generate study plan") }
    var type by remember { mutableStateOf(RoutineType.CUSTOM) }
    var confirmBeforeRun by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Smart Routine") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Routine Name") }, singleLine = true)
                OutlinedTextField(value = trigger, onValueChange = { trigger = it }, label = { Text("Voice Trigger Phrase") }, singleLine = true)
                OutlinedTextField(value = stepsRaw, onValueChange = { stepsRaw = it }, label = { Text("Action Steps (comma separated)") })
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(RoutineType.entries) { rt ->
                        FilterChip(selected = type == rt, onClick = { type = rt }, label = { Text(rt.name) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = confirmBeforeRun, onCheckedChange = { confirmBeforeRun = it })
                    Text("Ask confirmation before running", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val steps = stepsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (name.isNotBlank() && trigger.isNotBlank() && steps.isNotEmpty()) {
                        onSave(name, trigger, type, steps, confirmBeforeRun)
                    }
                },
                enabled = name.isNotBlank() && trigger.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
