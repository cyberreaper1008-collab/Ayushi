package com.example.data

import android.graphics.Bitmap
import android.util.Log
import com.example.core.AssistantMode
import com.example.model.ChatMessage
import com.example.model.ExamTrack
import com.example.model.PersonalMemory
import com.example.model.PreferredLanguage
import com.example.model.ProductivityTask
import com.example.model.ResearchResult
import com.example.model.StudyQuizQuestion
import com.example.model.UserProfile
import com.example.model.WebSourceLink
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject

private const val TAG = "JarvisAiEngine"
private const val AI_TIMEOUT_MS = 60_000L

data class AiToolExecutionResult(
    val responsePayload: JsonObject,
    val executedSummary: String? = null
)

data class AssistantTurnOutput(
    val replyText: String,
    val actionSummaries: List<String> = emptyList(),
    val sources: List<WebSourceLink> = emptyList()
)

class JarvisAiEngine {

    private val functionDeclarations = listOf(
        FunctionDeclaration(
            name = "create_task_or_reminder",
            description = "Create a new task, reminder, habit, or study timetable item in the user's JARVIS productivity planner.",
            parameters = mapOf(
                "title" to Schema.string("Short task or reminder title"),
                "details" to Schema.string("Optional notes or steps"),
                "category" to Schema.enumeration(listOf("TASK", "REMINDER", "HABIT", "STUDY"), "Item category"),
                "priority" to Schema.enumeration(listOf("HIGH", "MEDIUM", "LOW"), "Task priority"),
                "dueLabel" to Schema.string("Human-readable time or day label like '7:00 PM' or 'Today'")
            ),
            optionalParameters = listOf("details", "dueLabel")
        ),
        FunctionDeclaration(
            name = "save_note",
            description = "Save a note or summary into the user's JARVIS Notes manager.",
            parameters = mapOf(
                "title" to Schema.string("Note title"),
                "content" to Schema.string("Full note body"),
                "tag" to Schema.string("Short category tag like Physics, Work, Personal")
            ),
            optionalParameters = listOf("tag")
        ),
        FunctionDeclaration(
            name = "save_memory",
            description = "Save a long-term user preference, ongoing project, or study goal into JARVIS Personal Memory. Never save passwords, OTPs, or secrets.",
            parameters = mapOf(
                "keyLabel" to Schema.string("Short label like 'Exam Track' or 'Preferred Name'"),
                "fact" to Schema.string("The fact or preference to remember"),
                "category" to Schema.enumeration(listOf("PREFERENCE", "PROJECT", "STUDY", "GENERAL"), "Memory category")
            )
        ),
        FunctionDeclaration(
            name = "open_app",
            description = "Launch an installed Android application by its display name (for example YouTube, Chrome, Settings, Calculator, WhatsApp).",
            parameters = mapOf(
                "appName" to Schema.string("Display name of the app to open")
            )
        ),
        FunctionDeclaration(
            name = "set_alarm_or_timer",
            description = "Set a device alarm at a specific hour and minute, or start a countdown timer in seconds.",
            parameters = mapOf(
                "hour" to Schema.integer("Hour in 24h format (0-23) for an alarm"),
                "minute" to Schema.integer("Minute (0-59) for an alarm"),
                "timerSeconds" to Schema.integer("Duration in seconds if setting a timer instead of an alarm"),
                "label" to Schema.string("Label for the alarm or timer")
            ),
            optionalParameters = listOf("hour", "minute", "timerSeconds", "label")
        ),
        FunctionDeclaration(
            name = "open_website",
            description = "Open a website URL in the device browser.",
            parameters = mapOf(
                "url" to Schema.string("Full https URL to open")
            )
        ),
        FunctionDeclaration(
            name = "start_navigation",
            description = "Start maps navigation to a destination address or place.",
            parameters = mapOf(
                "destination" to Schema.string("Destination place or address")
            )
        ),
        FunctionDeclaration(
            name = "open_device_settings",
            description = "Open a specific Android device settings page such as wifi, bluetooth, display, sound, battery, or permissions.",
            parameters = mapOf(
                "settingType" to Schema.string("Settings category: wifi, bluetooth, display, sound, battery, permissions, or general")
            )
        ),
        FunctionDeclaration(
            name = "share_text",
            description = "Open the Android system share sheet to share text with other apps.",
            parameters = mapOf(
                "text" to Schema.string("Text content to share")
            )
        ),
        FunctionDeclaration(
            name = "get_device_status",
            description = "Read current device battery percentage, charging status, network state, and installed app count.",
            parameters = emptyMap()
        ),
        FunctionDeclaration(
            name = "open_jarvis_module",
            description = "Navigate inside JARVIS to a module: productivity, study, research, or settings.",
            parameters = mapOf(
                "moduleName" to Schema.enumeration(listOf("productivity", "study", "research", "settings"), "Target JARVIS module")
            )
        )
    )

    private val ai by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
    }

    private fun buildSystemPrompt(
        profile: UserProfile,
        memories: List<PersonalMemory>,
        tasks: List<ProductivityTask>,
        mode: AssistantMode
    ): String {
        val approvedMemories = memories.filter { it.approved }.take(15)
        val memoryBlock = if (approvedMemories.isEmpty()) {
            "None stored yet."
        } else {
            approvedMemories.joinToString("\n") { "- [${it.category}] ${it.keyLabel}: ${it.fact}" }
        }
        val activeTasks = tasks.filter { !it.completed }.take(10)
        val tasksBlock = if (activeTasks.isEmpty()) {
            "No pending tasks."
        } else {
            activeTasks.joinToString("\n") { "- (${it.priority}) ${it.title} [${it.category}, due: ${it.dueLabel}]" }
        }

        val langInstruction = when (PreferredLanguage.fromRaw(profile.preferredLanguage)) {
            PreferredLanguage.Hindi -> "Respond primarily in natural, clear Hindi (Devanagari script or simple conversational Hindi matching the user's tone)."
            PreferredLanguage.English -> "Respond in crisp, articulate English."
            PreferredLanguage.Hinglish -> "Understand English, Hindi, and Hinglish seamlessly. Match the user's language naturally (reply in Hinglish/Hindi if they speak Hindi/Hinglish, or English if they speak English)."
        }

        val modeDirective = when (mode) {
            AssistantMode.ASSISTANT -> "Act as JARVIS: intelligent, calm, loyal, futuristic, concise for quick commands and detailed when depth is needed."
            AssistantMode.STUDY -> "You are in JARVIS Study Mode (Physics, Chemistry, Biology, Math, JEE & NEET mentor). Break down concepts step-by-step with formulas, intuition, and exam tips."
            AssistantMode.RESEARCH -> "You are in JARVIS Research Mode. Synthesize structured, balanced insights and clearly distinguish verified facts from uncertain details."
        }

        return """
            You are JARVIS (Just A Rather Very Intelligent System), a personal AI assistant running natively on ${profile.displayName}'s Android smartphone.
            Personality: Intelligent, professional, friendly, calm, and futuristic. Address the user naturally (e.g., "${profile.displayName}" or "Sir/Ma'am" sparingly).
            $modeDirective
            $langInstruction

            SECURITY & TRUTHFULNESS RULES (MANDATORY):
            - Never fabricate successful actions. Only claim an action succeeded if its tool call returned success or proposal queued.
            - If a tool reports an error or requires user confirmation, clearly state why or inform the user that a confirmation prompt is awaiting their approval.
            - Never ask for or store passwords, OTPs, banking PINs, or private credentials.

            USER CUSTOM INSTRUCTIONS:
            ${profile.customInstructions.ifBlank { "None" }}

            USER-APPROVED PERSONAL MEMORIES:
            $memoryBlock

            CURRENT PENDING TASKS & REMINDERS:
            $tasksBlock
        """.trimIndent()
    }

    suspend fun sendAssistantTurn(
        prompt: String,
        history: List<ChatMessage>,
        profile: UserProfile,
        memories: List<PersonalMemory>,
        tasks: List<ProductivityTask>,
        mode: AssistantMode,
        attachedBitmap: Bitmap? = null,
        attachedDocBytes: ByteArray? = null,
        attachedDocMime: String? = null,
        toolExecutor: suspend (FunctionCallPart) -> AiToolExecutionResult
    ): AssistantTurnOutput {
        val model = ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(buildSystemPrompt(profile, memories, tasks, mode))
            },
            tools = listOf(Tool.functionDeclarations(functionDeclarations))
        )

        val chatHistory: List<Content> = history
            .filter { it.role == "user" || it.role == "model" }
            .takeLast(14)
            .map { msg ->
                content(msg.role) { text(msg.text) }
            }

        val chat = model.startChat(history = chatHistory)
        val actionSummaries = mutableListOf<String>()

        return withTimeout(AI_TIMEOUT_MS) {
            val userContent = content("user") {
                if (attachedBitmap != null) {
                    image(attachedBitmap)
                }
                if (attachedDocBytes != null && !attachedDocMime.isNullOrBlank()) {
                    inlineData(attachedDocBytes, attachedDocMime)
                }
                text(prompt)
            }

            var response = chat.sendMessage(userContent)
            var hops = 0
            while (response.functionCalls.isNotEmpty() && hops < 4) {
                hops++
                val functionResponses = mutableListOf<FunctionResponsePart>()
                for (call in response.functionCalls) {
                    val result = toolExecutor(call)
                    result.executedSummary?.let { actionSummaries.add(it) }
                    functionResponses.add(FunctionResponsePart(call.name, result.responsePayload))
                }
                response = chat.sendMessage(
                    content("user") {
                        functionResponses.forEach { part(it) }
                    }
                )
            }

            val finalReply = response.text?.trim().orEmpty()
                .ifEmpty {
                    if (actionSummaries.isNotEmpty()) {
                        actionSummaries.joinToString("\n")
                    } else {
                        "I processed your request, though no text output was returned."
                    }
                }

            AssistantTurnOutput(
                replyText = finalReply,
                actionSummaries = actionSummaries
            )
        }
    }

    suspend fun researchWithSearch(
        query: String,
        webpageUrl: String?,
        language: PreferredLanguage
    ): ResearchResult {
        val useUrlContext = !webpageUrl.isNullOrBlank()
        val tools = if (useUrlContext) {
            listOf(Tool.googleSearch(), Tool.urlContext())
        } else {
            listOf(Tool.googleSearch())
        }

        val model = ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "You are JARVIS Research Assistant. Provide accurate, structured research in ${language.label}. " +
                        "Always distinguish verified facts backed by retrieved sources from uncertain or unverified claims. " +
                        "Include concise bullet takeaways."
                )
            },
            tools = tools
        )

        val fullPrompt = if (useUrlContext) {
            "Analyze and summarize this URL: $webpageUrl\nUser request: $query"
        } else {
            query
        }

        return withTimeout(AI_TIMEOUT_MS) {
            val response = model.generateContent(fullPrompt)
            val candidate = response.candidates.firstOrNull()
            val grounding = candidate?.groundingMetadata
            val sources = grounding?.groundingChunks
                ?.mapNotNull { chunk ->
                    val web = chunk.web
                    val uri = web?.uri
                    if (!uri.isNullOrBlank()) {
                        WebSourceLink(
                            title = web.title?.takeIf { it.isNotBlank() } ?: uri,
                            uri = uri
                        )
                    } else null
                }
                ?.distinctBy { it.uri }
                ?.take(8)
                ?: emptyList()

            val widgetHtml = grounding?.searchEntryPoint?.renderedContent
            ResearchResult(
                answer = response.text?.trim().orEmpty()
                    .ifEmpty { "No grounded summary could be generated for this query." },
                sources = sources,
                searchWidgetHtml = widgetHtml,
                isUrlSummary = useUrlContext
            )
        }
    }

    suspend fun generateStudyMcqQuiz(
        subject: String,
        examTrack: ExamTrack,
        topic: String,
        language: PreferredLanguage
    ): List<StudyQuizQuestion> {
        val questionSchema = Schema.obj(
            properties = mapOf(
                "question" to Schema.string("Clear exam-level MCQ question stem"),
                "options" to Schema.array(Schema.string(), "Exactly 4 answer choices"),
                "correctIndex" to Schema.integer("Zero-based index (0 to 3) of the correct option"),
                "explanation" to Schema.string("Step-by-step solution explanation")
            )
        )
        val quizSchema = Schema.obj(
            properties = mapOf(
                "questions" to Schema.array(questionSchema, "List of 5 practice MCQs")
            )
        )

        val model = ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = quizSchema
            }
        )

        val prompt = """
            Generate 5 high-quality multiple choice questions for ${examTrack.name} preparation.
            Subject: $subject
            Topic: $topic
            Language: ${language.label} (use clear technical terms with simple ${language.label} explanation).
            Ensure each question has exactly 4 distinct options and correctIndex is between 0 and 3.
        """.trimIndent()

        return withTimeout(AI_TIMEOUT_MS) {
            val response = model.generateContent(prompt)
            val raw = response.text ?: throw IllegalStateException("Quiz model returned empty output.")
            val root = JSONObject(raw)
            val arr = root.getJSONArray("questions")
            (0 until arr.length()).map { idx ->
                val qObj = arr.getJSONObject(idx)
                val optsArr = qObj.getJSONArray("options")
                val opts = (0 until optsArr.length()).map { optsArr.getString(it) }
                StudyQuizQuestion(
                    question = qObj.getString("question"),
                    options = opts,
                    correctIndex = qObj.optInt("correctIndex", 0).coerceIn(0, (opts.size - 1).coerceAtLeast(0)),
                    explanation = qObj.optString("explanation", "")
                )
            }
        }
    }

    suspend fun generateStudyResource(
        subject: String,
        examTrack: ExamTrack,
        topic: String,
        resourceType: String,
        language: PreferredLanguage
    ): String {
        val model = ai.generativeModel(modelName = "gemini-3.8-flash")
        val prompt = """
            You are JARVIS Study Mentor. Create a crisp, high-yield $resourceType for ${examTrack.name} students.
            Subject: $subject
            Topic: $topic
            Language: ${language.label}
            Include key laws/equations, common exam traps, and clear structure.
        """.trimIndent()

        return withTimeout(AI_TIMEOUT_MS) {
            val response = model.generateContent(prompt)
            response.text?.trim().orEmpty().ifEmpty { "Could not generate $resourceType." }
        }
    }

    suspend fun organizeDailyPlan(
        tasks: List<ProductivityTask>,
        language: PreferredLanguage
    ): String {
        val model = ai.generativeModel(modelName = "gemini-3.8-flash")
        val taskList = if (tasks.isEmpty()) {
            "No tasks currently logged."
        } else {
            tasks.joinToString("\n") {
                "- [${if (it.completed) "DONE" else "OPEN"}] (${it.priority}) ${it.title} | ${it.category} | Due: ${it.dueLabel}"
            }
        }
        val prompt = """
            As JARVIS, organize my day based on these tasks and habits in ${language.label}.
            Provide:
            1. Top 3 Priority Focus items
            2. Recommended Time-Block Schedule
            3. Quick motivation / daily summary
            Tasks:
            $taskList
        """.trimIndent()

        return withTimeout(AI_TIMEOUT_MS) {
            val response = model.generateContent(prompt)
            response.text?.trim().orEmpty().ifEmpty { "Unable to generate daily plan." }
        }
    }

    suspend fun summarizeConversation(
        messages: List<ChatMessage>,
        language: PreferredLanguage
    ): String {
        if (messages.isEmpty()) return "No conversation messages to summarize yet."
        val model = ai.generativeModel(modelName = "gemini-3.5-flash-lite")
        val transcript = messages.takeLast(25).joinToString("\n") { "${it.role.uppercase()}: ${it.text}" }
        val prompt = "Summarize this JARVIS conversation into concise bullet points and action items in ${language.label}:\n\n$transcript"
        return withTimeout(AI_TIMEOUT_MS) {
            val response = model.generateContent(prompt)
            response.text?.trim().orEmpty().ifEmpty { "Summary unavailable." }
        }
    }

    companion object {
        fun Map<String, JsonElement>.str(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull

        fun Map<String, JsonElement>.intVal(key: String): Int? =
            this[key]?.jsonPrimitive?.intOrNull

        fun successJson(message: String): JsonObject = JsonObject(
            mapOf("status" to JsonPrimitive("ok"), "message" to JsonPrimitive(message))
        )

        fun pendingJson(message: String): JsonObject = JsonObject(
            mapOf("status" to JsonPrimitive("awaiting_confirmation"), "message" to JsonPrimitive(message))
        )

        fun errorJson(message: String): JsonObject = JsonObject(
            mapOf("status" to JsonPrimitive("error"), "message" to JsonPrimitive(message))
        )
    }
}
