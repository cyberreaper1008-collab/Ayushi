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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.UiState
import com.example.model.ResearchResult
import com.example.ui.components.GlassHudCard
import com.example.ui.components.SectionHeaderRow
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusEmerald
import com.example.viewmodel.JarvisViewModel

@Composable
fun ResearchScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val researchState by viewModel.researchState.collectAsStateWithLifecycle()

    var queryText by rememberSaveable { mutableStateOf("") }
    var urlText by rememberSaveable { mutableStateOf("") }

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
                    title = "WEB RESEARCH ASSISTANT",
                    subtitle = "Grounded Google Search • Webpage Summarizer • Source Verification"
                )
            }

            item {
                GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        label = { Text("Research Topic or Question") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("research_query_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        label = { Text("Optional Webpage URL to Summarize") },
                        placeholder = { Text("https://...") },
                        leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("research_url_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.runWebResearch(queryText, urlText.ifBlank { null }) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("research_run_button")
                        ) {
                            Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Research & Verify")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            AssistChip(
                                onClick = {
                                    queryText = "Latest breakthroughs in quantum computing 2026"
                                    viewModel.runWebResearch(queryText, null)
                                },
                                label = { Text("Quantum Computing") }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    queryText = "Compare Kotlin Multiplatform vs Flutter tutorials"
                                    viewModel.runWebResearch(queryText, null)
                                },
                                label = { Text("Find Tutorials") }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    queryText = "JEE Main 2027 syllabus and preparation strategy"
                                    viewModel.runWebResearch(queryText, null)
                                },
                                label = { Text("JEE Strategy") }
                            )
                        }
                    }
                }
            }

            when (val r = researchState) {
                null -> item {
                    GlassHudCard(Modifier.fillMaxWidth()) {
                        Text(
                            text = "Enter a topic or paste a public URL above. JARVIS queries Google Search Grounding and clearly separates verified source links from uncertain claims.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is UiState.Loading -> item {
                    GlassHudCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Searching and verifying sources...")
                        }
                    }
                }

                is UiState.Error -> item {
                    GlassHudCard(Modifier.fillMaxWidth()) {
                        Text(r.message, color = MaterialTheme.colorScheme.error)
                    }
                }

                is UiState.Success -> {
                    item {
                        ResearchResultCard(
                            result = r.data,
                            query = queryText.ifBlank { urlText },
                            onOpenSource = { viewModel.openExternalUrl(it) },
                            onSaveNote = {
                                val srcBlock = if (r.data.sources.isNotEmpty()) {
                                    "\n\nSources:\n" + r.data.sources.joinToString("\n") { "- ${it.title}: ${it.uri}" }
                                } else ""
                                viewModel.addNote(
                                    title = "Research: ${queryText.ifBlank { urlText }.take(40)}",
                                    content = r.data.answer + srcBlock,
                                    tag = "Research"
                                )
                            },
                            onShare = {
                                viewModel.shareContent("JARVIS Research", r.data.answer)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResearchResultCard(
    result: ResearchResult,
    query: String,
    onOpenSource: (String) -> Unit,
    onSaveNote: () -> Unit,
    onShare: () -> Unit
) {
    GlassHudCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Verified,
                    contentDescription = null,
                    tint = if (result.sources.isNotEmpty()) StatusEmerald else StatusAmber,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (result.sources.isNotEmpty()) {
                        "GROUNDED WITH ${result.sources.size} WEB SOURCES"
                    } else {
                        "GENERAL KNOWLEDGE (VERIFY INDEPENDENTLY)"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (result.sources.isNotEmpty()) StatusEmerald else StatusAmber
                )
            }
            Row {
                IconButton(onClick = onSaveNote) {
                    Icon(Icons.Filled.BookmarkAdd, contentDescription = "Save research to notes")
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = "Share research")
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = result.answer,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (result.sources.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "VERIFIED SOURCES (GOOGLE SEARCH)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            result.sources.forEach { src ->
                OutlinedButton(
                    onClick = { onOpenSource(src.uri) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = src.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.OpenInNew,
                            contentDescription = "Open ${src.title}",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
