package com.example.data

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.model.ChatMessage
import com.example.model.HudTheme
import com.example.model.JarvisNote
import com.example.model.MemoryCategory
import com.example.model.PersonalMemory
import com.example.model.PreferredLanguage
import com.example.model.ProductivityTask
import com.example.model.RoutineType
import com.example.model.SmartRoutine
import com.example.model.StudyProgress
import com.example.model.TaskCategory
import com.example.model.TaskPriority
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class JarvisFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    // --- Profile ---

    fun observeProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            userDoc(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(displayName: String?): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val docRef = userDoc(uid)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val initialProfile = UserProfile(
                    userId = uid,
                    displayName = displayName?.takeIf { it.isNotBlank() }?.take(80) ?: "Commander"
                )
                docRef.set(initialProfile.toCreateMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProfileSettings(updates: Map<String, Any>): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val sanitized = updates.toMutableMap().apply {
                remove("userId")
                remove("createdAt")
                remove("id")
                put("updatedAt", FieldValue.serverTimestamp())
            }
            userDoc(uid).update(sanitized).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Conversations ---

    fun observeConversations(userId: String): Flow<List<ChatMessage>> = flow {
        val path = "users/$userId/conversations"
        emitAll(
            userDoc(userId).collection("conversations")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(120)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun addMessage(message: ChatMessage): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/conversations"
        return try {
            val uid = requireUserId()
            val ref = userDoc(uid).collection("conversations").document()
            docPath = ref.path
            ref.set(message.toCreateMap(uid)).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun clearConversations(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid/conversations"
        return try {
            val snap = userDoc(uid).collection("conversations").get().await()
            val chunks = snap.documents.chunked(400)
            for (chunk in chunks) {
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Productivity Tasks ---

    fun observeTasks(userId: String): Flow<List<ProductivityTask>> = flow {
        val path = "users/$userId/tasks"
        emitAll(
            userDoc(userId).collection("tasks")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ProductivityTask::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun addTask(task: ProductivityTask): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/tasks"
        return try {
            val uid = requireUserId()
            val ref = userDoc(uid).collection("tasks").document()
            docPath = ref.path
            ref.set(task.toCreateMap(uid)).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleTaskCompletion(task: ProductivityTask): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/tasks/${task.id}"
        return try {
            val uid = requireUserId()
            require(task.id.isNotBlank()) { "Task ID is required" }
            val ref = userDoc(uid).collection("tasks").document(task.id)
            docPath = ref.path
            val nextCompleted = !task.completed
            val nextStreak = if (task.category == TaskCategory.HABIT.name && nextCompleted) {
                (task.streakCount + 1L).coerceAtMost(10000L)
            } else {
                task.streakCount.coerceIn(0L, 10000L)
            }
            ref.update(
                mapOf(
                    "completed" to nextCompleted,
                    "streakCount" to nextStreak,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateTaskPriority(taskId: String, priority: TaskPriority): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/tasks/$taskId"
        return try {
            val uid = requireUserId()
            require(taskId.isNotBlank()) { "Task ID is required" }
            val ref = userDoc(uid).collection("tasks").document(taskId)
            docPath = ref.path
            ref.update(
                mapOf(
                    "priority" to priority.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(taskId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/tasks/$taskId"
        return try {
            val uid = requireUserId()
            require(taskId.isNotBlank())
            val ref = userDoc(uid).collection("tasks").document(taskId)
            docPath = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- Notes ---

    fun observeNotes(userId: String): Flow<List<JarvisNote>> = flow {
        val path = "users/$userId/notes"
        emitAll(
            userDoc(userId).collection("notes")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        JarvisNote::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun addNote(note: JarvisNote): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/notes"
        return try {
            val uid = requireUserId()
            val ref = userDoc(uid).collection("notes").document()
            docPath = ref.path
            ref.set(note.toCreateMap(uid)).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleNotePin(note: JarvisNote): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/notes/${note.id}"
        return try {
            val uid = requireUserId()
            require(note.id.isNotBlank())
            val ref = userDoc(uid).collection("notes").document(note.id)
            docPath = ref.path
            ref.update(
                mapOf(
                    "pinned" to !note.pinned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteNote(noteId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/notes/$noteId"
        return try {
            val uid = requireUserId()
            require(noteId.isNotBlank())
            val ref = userDoc(uid).collection("notes").document(noteId)
            docPath = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- Personal Memories ---

    fun observeMemories(userId: String): Flow<List<PersonalMemory>> = flow {
        val path = "users/$userId/memories"
        emitAll(
            userDoc(userId).collection("memories")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PersonalMemory::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun addMemory(memory: PersonalMemory): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/memories"
        return try {
            val uid = requireUserId()
            val ref = userDoc(uid).collection("memories").document()
            docPath = ref.path
            ref.set(memory.toCreateMap(uid)).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateMemory(memoryId: String, keyLabel: String, fact: String, approved: Boolean): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/memories/$memoryId"
        return try {
            val uid = requireUserId()
            require(memoryId.isNotBlank())
            val ref = userDoc(uid).collection("memories").document(memoryId)
            docPath = ref.path
            ref.update(
                mapOf(
                    "keyLabel" to keyLabel.trim().take(100).ifEmpty { "Preference" },
                    "fact" to fact.trim().take(500).ifEmpty { "Saved preference" },
                    "approved" to approved,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(memoryId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/memories/$memoryId"
        return try {
            val uid = requireUserId()
            require(memoryId.isNotBlank())
            val ref = userDoc(uid).collection("memories").document(memoryId)
            docPath = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- Smart Routines ---

    fun observeRoutines(userId: String): Flow<List<SmartRoutine>> = flow {
        val path = "users/$userId/routines"
        emitAll(
            userDoc(userId).collection("routines")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SmartRoutine::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun addRoutine(routine: SmartRoutine): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/routines"
        return try {
            val uid = requireUserId()
            val ref = userDoc(uid).collection("routines").document()
            docPath = ref.path
            ref.set(routine.toCreateMap(uid)).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleRoutineEnabled(routine: SmartRoutine): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/routines/${routine.id}"
        return try {
            val uid = requireUserId()
            require(routine.id.isNotBlank())
            val ref = userDoc(uid).collection("routines").document(routine.id)
            docPath = ref.path
            ref.update(
                mapOf(
                    "enabled" to !routine.enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteRoutine(routineId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/routines/$routineId"
        return try {
            val uid = requireUserId()
            require(routineId.isNotBlank())
            val ref = userDoc(uid).collection("routines").document(routineId)
            docPath = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- Study Progress ---

    fun observeStudyLogs(userId: String): Flow<List<StudyProgress>> = flow {
        val path = "users/$userId/study_logs"
        emitAll(
            userDoc(userId).collection("study_logs")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StudyProgress::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun recordStudySession(progress: StudyProgress): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/study_logs"
        return try {
            val uid = requireUserId()
            val ref = userDoc(uid).collection("study_logs").document()
            docPath = ref.path
            ref.set(progress.toCreateMap(uid)).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    private fun logStreamError(error: Throwable, op: OperationType, path: String, expectedUid: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }
}
