package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ActionResult
import com.example.data.model.ActionType
import com.example.service.AndroidActionBridge
import com.example.service.AndroidActionManager
import com.example.service.AudioRecordManager
import com.example.service.AudioTrackPlayer
import com.example.service.NecxaGeminiLiveService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AssistantStatus {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    EXECUTING_ACTION
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class NecxaUiState(
    val status: AssistantStatus = AssistantStatus.IDLE,
    val isMicActive: Boolean = false,
    val audioLevel: Float = 0f,
    val detectedLanguage: String = "Auto (Multi-lingual)",
    val connectionStatus: String = "Ready",
    val isConnected: Boolean = false,
    val lastActionResult: ActionResult? = null,
    val actionHistory: List<ActionResult> = emptyList(),
    val messages: List<ChatMessage> = listOf(
        ChatMessage(
            sender = "NECXA",
            text = "Haan boss! Main online hoon. 'Nexa' bolo ya mic daba ke baat karo, boliye kya kaam hai?"
        )
    ),
    val hasAudioPermission: Boolean = false,
    val hasContactsPermission: Boolean = false,
    val hasCallPermission: Boolean = false
)

data class TestCaseItem(
    val id: Int,
    val title: String,
    val prompt: String,
    val expected: String
)

class NecxaViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(NecxaUiState())
    val uiState: StateFlow<NecxaUiState> = _uiState.asStateFlow()

    val actionManager = AndroidActionManager(application.applicationContext)
    private val audioRecordManager = AudioRecordManager(application.applicationContext, viewModelScope)
    private val audioTrackPlayer = AudioTrackPlayer(viewModelScope)
    private val liveService = NecxaGeminiLiveService(actionManager, viewModelScope)

    val actionBridge = AndroidActionBridge(actionManager) { fnName, success, details ->
        viewModelScope.launch {
            val type = when (fnName) {
                "openWhatsApp" -> ActionType.OPEN_WHATSAPP
                "sendWhatsAppMessage" -> ActionType.SEND_WHATSAPP_MESSAGE
                "openApp" -> ActionType.OPEN_APP
                "openUrl" -> ActionType.OPEN_URL
                "makeCall" -> ActionType.MAKE_CALL
                "callContact" -> ActionType.CALL_CONTACT
                else -> ActionType.GENERAL_QUERY
            }
            val result = ActionResult(type, success, details, target = fnName)
            recordAction(result)
        }
    }

    val officialTestCases = listOf(
        TestCaseItem(1, "Hello Necxa", "Hello Necxa.", "Audible voice greeting"),
        TestCaseItem(2, "Hindi Switch", "Hindi mein baat karo.", "Switches to Hindi responses"),
        TestCaseItem(3, "English Switch", "Talk to me in English.", "Switches to English responses"),
        TestCaseItem(4, "Hinglish Switch", "Hinglish mein baat karo.", "Responds naturally in Hinglish"),
        TestCaseItem(5, "Open WhatsApp (Hindi)", "WhatsApp kholo.", "Executes WhatsApp launch"),
        TestCaseItem(6, "Open WhatsApp (English)", "Open WhatsApp.", "Executes WhatsApp launch"),
        TestCaseItem(7, "Call Mom", "Mummy ko call karo.", "Finds Mom contact, initiates call"),
        TestCaseItem(8, "Call Rahul", "Call Rahul.", "Finds Rahul; asks clarification if multiple"),
        TestCaseItem(9, "Direct Call", "Call 9876543210.", "Opens dialer / initiates call to number"),
        TestCaseItem(10, "Interrupt Speech", "Stop", "Interrupts ongoing speech immediately")
    )

    init {
        setupListeners()
        liveService.connect()
        viewModelScope.launch {
            com.example.service.NecxaVoiceService.wakeWordEvents.collect { _ ->
                if (!_uiState.value.isMicActive) {
                    startListening()
                }
            }
        }
    }

    private fun setupListeners() {
        audioRecordManager.onAudioChunk = { chunk ->
            liveService.sendAudioChunk(chunk)
        }

        audioRecordManager.onAmplitudeChanged = { amp ->
            if (_uiState.value.status == AssistantStatus.LISTENING) {
                _uiState.update { it.copy(audioLevel = amp) }
            }
        }

        audioTrackPlayer.onPlaybackStarted = {
            _uiState.update { it.copy(status = AssistantStatus.SPEAKING) }
        }

        audioTrackPlayer.onPlaybackEnded = {
            if (_uiState.value.status == AssistantStatus.SPEAKING) {
                _uiState.update { it.copy(status = AssistantStatus.IDLE, audioLevel = 0f) }
            }
        }

        audioTrackPlayer.onAmplitudeChanged = { amp ->
            if (_uiState.value.status == AssistantStatus.SPEAKING) {
                _uiState.update { it.copy(audioLevel = amp) }
            }
        }

        liveService.onConnectionStateChanged = { connected, status ->
            _uiState.update { it.copy(isConnected = connected, connectionStatus = status) }
        }

        liveService.onAudioReceived = { pcmChunk ->
            audioTrackPlayer.enqueueChunk(pcmChunk)
        }

        liveService.onTranscriptReceived = { speaker, text ->
            _uiState.update { state ->
                val newMsg = ChatMessage(sender = speaker, text = text)
                state.copy(
                    messages = (state.messages + newMsg).takeLast(20),
                    status = if (speaker == "Necxa" && audioTrackPlayer.isCurrentlyPlaying())
                        AssistantStatus.SPEAKING else state.status
                )
            }
        }

        liveService.onInterrupted = {
            interruptAssistant()
        }

        liveService.onActionExecuted = { result ->
            recordAction(result)
        }

        liveService.onLanguageDetected = { lang ->
            _uiState.update { it.copy(detectedLanguage = lang) }
        }
    }

    private fun recordAction(result: ActionResult) {
        _uiState.update { state ->
            state.copy(
                lastActionResult = result,
                actionHistory = (listOf(result) + state.actionHistory).take(15),
                status = AssistantStatus.EXECUTING_ACTION
            )
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(1800)
            if (_uiState.value.status == AssistantStatus.EXECUTING_ACTION) {
                _uiState.update { it.copy(status = if (audioTrackPlayer.isCurrentlyPlaying()) AssistantStatus.SPEAKING else AssistantStatus.IDLE) }
            }
        }
    }

    fun toggleMic() {
        if (_uiState.value.isMicActive) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun startListening() {
        // User interruption rule: if Necxa was speaking, interrupt immediately!
        interruptAssistant()

        val started = audioRecordManager.startRecording()
        if (started) {
            _uiState.update { it.copy(isMicActive = true, status = AssistantStatus.LISTENING) }
        } else {
            _uiState.update { it.copy(connectionStatus = "Microphone permission required") }
        }
    }

    fun stopListening() {
        audioRecordManager.stopRecording()
        _uiState.update { it.copy(isMicActive = false, status = AssistantStatus.IDLE, audioLevel = 0f) }
    }

    /**
     * Interrupts Necxa's current speech immediately
     */
    fun interruptAssistant() {
        audioTrackPlayer.interrupt()
        _uiState.update {
            it.copy(
                audioLevel = 0f,
                status = if (it.isMicActive) AssistantStatus.LISTENING else AssistantStatus.IDLE
            )
        }
    }

    fun sendTextCommand(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return

        // If assistant is speaking, interrupt first
        interruptAssistant()

        _uiState.update { state ->
            val userMsg = ChatMessage(sender = "You", text = trimmed)
            state.copy(
                messages = (state.messages + userMsg).takeLast(20),
                status = AssistantStatus.THINKING
            )
        }

        liveService.sendTextPrompt(trimmed)
    }

    fun runTestCase(testCase: TestCaseItem) {
        if (testCase.id == 10) {
            // Interruption test
            interruptAssistant()
            val msg = ChatMessage(sender = "Necxa", text = "Audio interrupted immediately. Ready for new command.")
            _uiState.update { it.copy(messages = (it.messages + msg).takeLast(20)) }
        } else {
            sendTextCommand(testCase.prompt)
        }
    }

    fun updatePermissions(audio: Boolean, contacts: Boolean, call: Boolean) {
        _uiState.update {
            it.copy(
                hasAudioPermission = audio,
                hasContactsPermission = contacts,
                hasCallPermission = call
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecordManager.stopRecording()
        audioTrackPlayer.release()
        liveService.disconnect()
    }
}
