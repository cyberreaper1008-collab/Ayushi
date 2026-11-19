package com.example.core

enum class JarvisAiStatus(val label: String) {
    SYSTEM_ONLINE("SYSTEM ONLINE"),
    LISTENING("LISTENING"),
    THINKING("THINKING"),
    PROCESSING("PROCESSING"),
    SPEAKING("SPEAKING"),
    TASK_COMPLETED("TASK COMPLETED")
}

enum class AssistantMode(val label: String) {
    ASSISTANT("ASSISTANT"),
    STUDY("STUDY"),
    RESEARCH("RESEARCH")
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
