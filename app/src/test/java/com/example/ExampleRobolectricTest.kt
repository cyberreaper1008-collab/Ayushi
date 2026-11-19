package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.ChatMessage
import com.example.model.HudTheme
import com.example.model.PersonalMemory
import com.example.model.PreferredLanguage
import com.example.model.ProductivityTask
import com.example.model.SmartRoutine
import com.example.model.StudyProgress
import com.example.model.TaskCategory
import com.example.model.TaskPriority
import com.example.model.UserProfile
import com.example.model.WebSourceLink
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.platform.DeviceIntegrationManager
import com.example.platform.VoiceController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app metadata and resources match JARVIS identity`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Jarvis", context.getString(R.string.app_name))
        assertEquals("Just A Rather Very Intelligent System", context.getString(R.string.jarvis_full_name))
        assertFalse(context.getString(R.string.firestore_database_id).isBlank())
    }

    @Test
    fun `firestore write maps omit DocumentId id and filter null optional fields`() {
        val uid = "user_abc"
        val msgMap = ChatMessage(
            id = "should_be_excluded",
            userId = uid,
            role = "model",
            text = "Systems online",
            mode = "ASSISTANT",
            attachmentLabel = null,
            actionSummary = null,
            sources = null
        ).toCreateMap(uid)

        assertFalse(msgMap.containsKey("id"))
        assertFalse(msgMap.containsKey("attachmentLabel"))
        assertFalse(msgMap.containsKey("actionSummary"))
        assertFalse(msgMap.containsKey("sources"))
        assertEquals("model", msgMap["role"])

        val profileMap = UserProfile(userId = uid, displayName = "Tony").toCreateMap(uid)
        assertFalse(profileMap.containsKey("id"))
        assertEquals("Hinglish", profileMap["preferredLanguage"])
        assertEquals(HudTheme.ARC_CYAN.name, profileMap["hudTheme"])

        val taskMap = ProductivityTask(
            id = "t1",
            userId = uid,
            title = "Revise Physics",
            category = TaskCategory.STUDY.name,
            priority = TaskPriority.HIGH.name
        ).toCreateMap(uid)
        assertFalse(taskMap.containsKey("id"))
        assertEquals("STUDY", taskMap["category"])
        assertEquals("HIGH", taskMap["priority"])

        val memMap = PersonalMemory(id = "m1", userId = uid, keyLabel = "Goal", fact = "JEE").toCreateMap(uid)
        assertFalse(memMap.containsKey("id"))

        val routineMap = SmartRoutine(id = "r1", userId = uid, name = "Morning", triggerPhrase = "morning", actionSteps = listOf("Brief")).toCreateMap(uid)
        assertFalse(routineMap.containsKey("id"))

        val studyMap = StudyProgress(id = "s1", userId = uid, subject = "Physics", topic = "Optics").toCreateMap(uid)
        assertFalse(studyMap.containsKey("id"))
    }

    @Test
    fun `enums and web source serialization handle unknown values safely`() {
        assertEquals(PreferredLanguage.Hinglish, PreferredLanguage.fromRaw("unknown"))
        assertEquals(HudTheme.ARC_CYAN, HudTheme.fromRaw(null))
        val source = WebSourceLink("Google AI", "https://ai.google.dev")
        val parsed = WebSourceLink.fromStorageString(source.toStorageString())
        assertNotNull(parsed)
        assertEquals("Google AI", parsed?.title)
        assertEquals("https://ai.google.dev", parsed?.uri)
    }

    @Test
    fun `device integration telemetry returns safe structure`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = DeviceIntegrationManager(context)
        val telemetry = manager.getDeviceTelemetry()
        assertTrue(telemetry.networkStatus.isNotBlank())
        val perms = manager.getPermissionStatus()
        assertNotNull(perms)
    }

    @Test
    fun `speech to text intent configures bilingual locales and error messages`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = VoiceController(context, onTranscriptReady = {}, onSpeechFinished = {})
        val hiIntent = controller.createSystemSpeechIntent(PreferredLanguage.Hindi)
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, hiIntent.action)
        assertEquals("hi-IN", hiIntent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertEquals(context.packageName, hiIntent.getStringExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE))
        assertTrue(hiIntent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false))

        val hinglishIntent = controller.createSystemSpeechIntent(PreferredLanguage.Hinglish)
        assertEquals("en-IN", hinglishIntent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))

        assertTrue(VoiceController.mapSpeechError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS).contains("Microphone", ignoreCase = true))
        assertTrue(VoiceController.mapSpeechError(SpeechRecognizer.ERROR_NETWORK).contains("Network", ignoreCase = true))
        controller.release()
    }
}
