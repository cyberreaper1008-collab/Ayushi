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
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.AssistantMode
import com.example.core.UiState
import com.example.model.ExamTrack
import com.example.model.StudyQuizQuestion
import com.example.ui.components.GlassHudCard
import com.example.ui.components.SectionHeaderRow
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusRed
import com.example.viewmodel.JarvisTab
import com.example.viewmodel.JarvisViewModel

@Composable
fun StudyScreen(
    viewModel: JarvisViewModel,
    onNavigateToTab: (JarvisTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val studyOutput by viewModel.studyOutput.collectAsStateWithLifecycle()
    val quizState by viewModel.quizState.collectAsStateWithLifecycle()
    val studyLogsState by viewModel.studyLogsState.collectAsStateWithLifecycle()

    var selectedTrack by rememberSaveable { mutableStateOf(ExamTrack.JEE) }
    var selectedSubject by rememberSaveable { mutableStateOf("Physics") }
    var topicInput by rememberSaveable { mutableStateOf("Electrostatics & Gauss's Law") }
    val selectedAnswers = remember { mutableStateMapOf<Int, Int>() }

    val subjects = listOf("Physics", "Chemistry", "Biology", "Mathematics")

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
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                SectionHeaderRow(
                    title = "STUDY ASSISTANT",
                    subtitle = "JEE • NEET • Concepts in Hindi, Hinglish & English"
                )
            }

            item {
                GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Exam Track", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ExamTrack.entries) { track ->
                            FilterChip(
                                selected = selectedTrack == track,
                                onClick = { selectedTrack = track },
                                label = { Text(track.name) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Subject", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(subjects) { subj ->
                            FilterChip(
                                selected = selectedSubject == subj,
                                onClick = { selectedSubject = subj },
                                label = { Text(subj) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = topicInput,
                        onValueChange = { topicInput = it },
                        label = { Text("Chapter, Topic, or Doubt") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("study_topic_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            Button(
                                onClick = {
                                    selectedAnswers.clear()
                                    viewModel.generateMcqQuiz(selectedSubject, selectedTrack, topicInput)
                                },
                                modifier = Modifier.testTag("study_mcq_button")
                            ) {
                                Icon(Icons.Filled.Quiz, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("MCQ Practice")
                            }
                        }
                        item {
                            OutlinedButton(onClick = {
                                viewModel.generateStudyMaterial(selectedSubject, selectedTrack, topicInput, "Formula Sheet")
                            }) { Text("Formula Sheet") }
                        }
                        item {
                            OutlinedButton(onClick = {
                                viewModel.generateStudyMaterial(selectedSubject, selectedTrack, topicInput, "Chapter Summary")
                            }) { Text("Chapter Summary") }
                        }
                        item {
                            OutlinedButton(onClick = {
                                viewModel.generateStudyMaterial(selectedSubject, selectedTrack, topicInput, "7-Day Revision Schedule")
                            }) { Text("Revision Plan") }
                        }
                        item {
                            OutlinedButton(onClick = {
                                viewModel.setAssistantMode(AssistantMode.STUDY)
                                onNavigateToTab(JarvisTab.CORE)
                                viewModel.sendUserMessage("Explain this ${selectedTrack.name} $selectedSubject topic clearly with step-by-step solved example: $topicInput")
                            }) { Text("Solve Doubt in Core") }
                        }
                    }
                }
            }

            if (studyOutput != null) {
                item {
                    GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "STUDY BRIEF • $selectedSubject",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (studyOutput is UiState.Success) {
                                val content = (studyOutput as UiState.Success<String>).data
                                Row {
                                    IconButton(onClick = { viewModel.speakMessage(content) }) {
                                        Icon(Icons.Filled.VolumeUp, contentDescription = "Read aloud")
                                    }
                                    IconButton(onClick = {
                                        viewModel.addNote("$selectedSubject: $topicInput", content, selectedSubject)
                                    }) {
                                        Icon(Icons.Filled.BookmarkAdd, contentDescription = "Save to Notes")
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        when (val s = studyOutput) {
                            is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(26.dp))
                            is UiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
                            is UiState.Success -> Text(s.data, style = MaterialTheme.typography.bodyMedium)
                            null -> Unit
                        }
                    }
                }
            }

            if (quizState != null) {
                item {
                    when (val q = quizState) {
                        is UiState.Loading -> GlassHudCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Generating ${selectedTrack.name} MCQs for $topicInput...")
                            }
                        }

                        is UiState.Error -> GlassHudCard(Modifier.fillMaxWidth()) {
                            Text(q.message, color = MaterialTheme.colorScheme.error)
                        }

                        is UiState.Success -> {
                            QuizInteractiveCard(
                                questions = q.data,
                                selectedAnswers = selectedAnswers,
                                onSelectAnswer = { qIdx, optIdx -> selectedAnswers[qIdx] = optIdx },
                                onSaveProgress = { solved, correct ->
                                    viewModel.saveStudyAttempt(
                                        subject = selectedSubject,
                                        examTrack = selectedTrack,
                                        topic = topicInput,
                                        solved = solved,
                                        correct = correct,
                                        summary = "MCQ Practice: $correct/$solved correct"
                                    )
                                }
                            )
                        }

                        null -> Unit
                    }
                }
            }

            item {
                Text(
                    text = "STUDY PROGRESS TRACKER",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            when (val logs = studyLogsState) {
                is UiState.Loading -> item { CircularProgressIndicator() }
                is UiState.Error -> item {
                    GlassHudCard(Modifier.fillMaxWidth()) {
                        Text(logs.message, color = MaterialTheme.colorScheme.error)
                    }
                }

                is UiState.Success -> {
                    if (logs.data.isEmpty()) {
                        item {
                            GlassHudCard(Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Complete an MCQ quiz above to log your JEE/NEET mastery progress.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(logs.data, key = { it.id }) { log ->
                            GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${log.subject} • ${log.topic}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${log.masteryPercent}%",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = StatusEmerald
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { (log.masteryPercent.toFloat() / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${log.examTrack} • ${log.correctCount}/${log.questionsSolved} solved • ${log.notesSummary}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizInteractiveCard(
    questions: List<StudyQuizQuestion>,
    selectedAnswers: Map<Int, Int>,
    onSelectAnswer: (Int, Int) -> Unit,
    onSaveProgress: (Int, Int) -> Unit
) {
    val answeredCount = selectedAnswers.size
    val correctCount = selectedAnswers.count { (qIdx, ansIdx) ->
        questions.getOrNull(qIdx)?.correctIndex == ansIdx
    }

    GlassHudCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MCQ PRACTICE ($answeredCount/${questions.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Score: $correctCount/${questions.size}",
                style = MaterialTheme.typography.labelLarge,
                color = StatusEmerald
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        questions.forEachIndexed { qIdx, q ->
            val chosen = selectedAnswers[qIdx]
            Text(
                text = "${qIdx + 1}. ${q.question}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))

            q.options.forEachIndexed { optIdx, optionText ->
                val isSelected = chosen == optIdx
                val isCorrect = optIdx == q.correctIndex
                val labelColor = when {
                    chosen == null -> MaterialTheme.colorScheme.onSurface
                    isCorrect -> StatusEmerald
                    isSelected -> StatusRed
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                OutlinedButton(
                    onClick = { onSelectAnswer(qIdx, optIdx) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${('A' + optIdx)}. $optionText",
                            color = labelColor,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (chosen != null && isCorrect) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Correct", tint = StatusEmerald, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            if (chosen != null && q.explanation.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Explanation: ${q.explanation}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Button(
            onClick = { onSaveProgress(questions.size, correctCount) },
            enabled = answeredCount > 0,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("save_study_score_button")
        ) {
            Text("Save Score to Study Tracker")
        }
    }
}
