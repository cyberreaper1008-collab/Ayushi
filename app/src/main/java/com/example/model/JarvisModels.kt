package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class PreferredLanguage(val label: String, val localeTag: String) {
    Hinglish("Hinglish", "en-IN"),
    English("English", "en-US"),
    Hindi("Hindi", "hi-IN");

    companion object {
        fun fromRaw(raw: String?): PreferredLanguage =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: Hinglish
    }
}

enum class HudTheme(val title: String) {
    ARC_CYAN("Arc Cyan"),
    STARK_GOLD("Stark Gold"),
    STEALTH_NAVY("Stealth Navy");

    companion object {
        fun fromRaw(raw: String?): HudTheme =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ARC_CYAN
    }
}

enum class TaskCategory {
    TASK, REMINDER, HABIT, STUDY;

    companion object {
        fun fromRaw(raw: String?): TaskCategory =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: TASK
    }
}

enum class TaskPriority {
    HIGH, MEDIUM, LOW;

    companion object {
        fun fromRaw(raw: String?): TaskPriority =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MEDIUM
    }
}

enum class MemoryCategory {
    PREFERENCE, PROJECT, STUDY, GENERAL;

    companion object {
        fun fromRaw(raw: String?): MemoryCategory =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: GENERAL
    }
}

enum class RoutineType(val label: String) {
    MORNING("Morning Routine"),
    STUDY("Study Routine"),
    REMINDER("Reminder Routine"),
    PLANNING("Daily Planning"),
    NIGHT("Night Routine"),
    CUSTOM("Custom Routine");

    companion object {
        fun fromRaw(raw: String?): RoutineType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CUSTOM
    }
}

enum class ExamTrack {
    JEE, NEET, BOARD, GENERAL;

    companion object {
        fun fromRaw(raw: String?): ExamTrack =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: GENERAL
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Sir",
    val preferredLanguage: String = "Hinglish",
    val speechSpeed: Double = 1.0,
    val speechPitch: Double = 1.0,
    val continuousVoice: Boolean = false,
    val wakeWordEnabled: Boolean = false,
    val hudTheme: String = "ARC_CYAN",
    val confirmSensitiveActions: Boolean = true,
    val autoSaveMemories: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val customInstructions: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "displayName" to displayName.ifBlank { "Sir" }.take(80),
        "preferredLanguage" to PreferredLanguage.fromRaw(preferredLanguage).name,
        "speechSpeed" to speechSpeed.coerceIn(0.5, 2.0),
        "speechPitch" to speechPitch.coerceIn(0.5, 1.8),
        "continuousVoice" to continuousVoice,
        "wakeWordEnabled" to wakeWordEnabled,
        "hudTheme" to HudTheme.fromRaw(hudTheme).name,
        "confirmSensitiveActions" to confirmSensitiveActions,
        "autoSaveMemories" to autoSaveMemories,
        "notificationsEnabled" to notificationsEnabled,
        "customInstructions" to customInstructions.take(1000),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "user",
    val text: String = "",
    val mode: String = "ASSISTANT",
    val attachmentLabel: String? = null,
    val actionSummary: String? = null,
    val sources: List<String>? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to uid,
            "role" to role.takeIf { it in listOf("user", "model", "system") }.orEmpty().ifEmpty { "user" },
            "text" to text.ifBlank { "..." }.take(16000),
            "mode" to mode.takeIf { it in listOf("ASSISTANT", "STUDY", "RESEARCH") }.orEmpty().ifEmpty { "ASSISTANT" },
            "attachmentLabel" to attachmentLabel?.take(200),
            "actionSummary" to actionSummary?.take(400),
            "sources" to sources?.take(10)?.takeIf { it.isNotEmpty() },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class ProductivityTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val details: String = "",
    val category: String = "TASK",
    val priority: String = "MEDIUM",
    val completed: Boolean = false,
    val dueLabel: String = "Today",
    val streakCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "title" to title.trim().take(200).ifEmpty { "Untitled Task" },
        "details" to details.trim().take(1000),
        "category" to TaskCategory.fromRaw(category).name,
        "priority" to TaskPriority.fromRaw(priority).name,
        "completed" to completed,
        "dueLabel" to dueLabel.trim().take(100),
        "streakCount" to streakCount.coerceIn(0L, 10000L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class JarvisNote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val tag: String = "General",
    val pinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "title" to title.trim().take(160).ifEmpty { "Untitled Note" },
        "content" to content.trim().take(8000).ifEmpty { "..." },
        "tag" to tag.trim().take(40).ifEmpty { "General" },
        "pinned" to pinned,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class PersonalMemory(
    @DocumentId val id: String = "",
    val userId: String = "",
    val keyLabel: String = "",
    val fact: String = "",
    val category: String = "GENERAL",
    val approved: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "keyLabel" to keyLabel.trim().take(100).ifEmpty { "Preference" },
        "fact" to fact.trim().take(500).ifEmpty { "Saved preference" },
        "category" to MemoryCategory.fromRaw(category).name,
        "approved" to approved,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class SmartRoutine(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val triggerPhrase: String = "",
    val routineType: String = "CUSTOM",
    val actionSteps: List<String> = emptyList(),
    val enabled: Boolean = false,
    val requiresConfirmation: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to name.trim().take(100).ifEmpty { "Custom Routine" },
        "triggerPhrase" to triggerPhrase.trim().take(100).ifEmpty { "run routine" },
        "routineType" to RoutineType.fromRaw(routineType).name,
        "actionSteps" to actionSteps.map { it.trim() }.filter { it.isNotEmpty() }.take(10)
            .ifEmpty { listOf("Open JARVIS briefing") },
        "enabled" to enabled,
        "requiresConfirmation" to requiresConfirmation,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class StudyProgress(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subject: String = "Physics",
    val topic: String = "",
    val examTrack: String = "JEE",
    val masteryPercent: Long = 0L,
    val questionsSolved: Long = 0L,
    val correctCount: Long = 0L,
    val notesSummary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "subject" to subject.trim().take(60).ifEmpty { "Physics" },
        "topic" to topic.trim().take(150).ifEmpty { "Core Concepts" },
        "examTrack" to ExamTrack.fromRaw(examTrack).name,
        "masteryPercent" to masteryPercent.coerceIn(0L, 100L),
        "questionsSolved" to questionsSolved.coerceIn(0L, 10000L),
        "correctCount" to correctCount.coerceIn(0L, 10000L),
        "notesSummary" to notesSummary.trim().take(2000),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

data class StudyQuizQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

data class ResearchResult(
    val answer: String,
    val sources: List<WebSourceLink>,
    val searchWidgetHtml: String? = null,
    val isUrlSummary: Boolean = false
)

data class WebSourceLink(
    val title: String,
    val uri: String
) {
    fun toStorageString(): String = "$title|$uri"

    companion object {
        fun fromStorageString(raw: String): WebSourceLink? {
            val idx = raw.lastIndexOf('|')
            if (idx <= 0 || idx >= raw.length - 1) return null
            return WebSourceLink(
                title = raw.substring(0, idx),
                uri = raw.substring(idx + 1)
            )
        }
    }
}

sealed interface PendingSensitiveAction {
    val title: String
    val description: String

    data class OpenAppAction(
        val appName: String,
        val packageName: String,
        override val title: String = "Launch Application",
        override val description: String = "Authorize JARVIS to open $appName?"
    ) : PendingSensitiveAction

    data class OpenUrlAction(
        val url: String,
        override val title: String = "Open External Website",
        override val description: String = "Open $url in your default browser?"
    ) : PendingSensitiveAction

    data class SetAlarmAction(
        val hour: Int,
        val minute: Int,
        val label: String,
        val timerSeconds: Int?,
        override val title: String = "Set Device Alarm / Timer",
        override val description: String
    ) : PendingSensitiveAction

    data class NavigationAction(
        val destination: String,
        override val title: String = "Start Navigation",
        override val description: String = "Launch navigation to \"$destination\"?"
    ) : PendingSensitiveAction

    data class OpenSettingsAction(
        val settingType: String,
        override val title: String = "Open Device Settings",
        override val description: String = "Open $settingType settings page?"
    ) : PendingSensitiveAction

    data class ShareTextAction(
        val text: String,
        override val title: String = "Share via Android",
        override val description: String = "Open Android Share Sheet with generated content?"
    ) : PendingSensitiveAction

    data class SaveMemoryAction(
        val keyLabel: String,
        val fact: String,
        val category: String,
        override val title: String = "Save to Personal Memory",
        override val description: String = "Remember \"$keyLabel\": $fact"
    ) : PendingSensitiveAction

    data class RunRoutineAction(
        val routine: SmartRoutine,
        override val title: String = "Execute Routine",
        override val description: String
    ) : PendingSensitiveAction
}
