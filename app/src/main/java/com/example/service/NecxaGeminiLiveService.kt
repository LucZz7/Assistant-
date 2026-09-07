package com.example.service

import android.util.Base64
import com.example.BuildConfig
import com.example.data.model.ActionResult
import com.example.data.model.ActionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NecxaGeminiLiveService(
    private val actionManager: AndroidActionManager,
    private val coroutineScope: CoroutineScope
) {
    private var webSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var isConnected = false
    private var reconnectJob: Job? = null

    var onConnectionStateChanged: ((isConnected: Boolean, status: String) -> Unit)? = null
    var onAudioReceived: ((ByteArray) -> Unit)? = null
    var onTranscriptReceived: ((speaker: String, text: String) -> Unit)? = null
    var onInterrupted: (() -> Unit)? = null
    var onActionExecuted: ((ActionResult) -> Unit)? = null
    var onLanguageDetected: ((String) -> Unit)? = null

    // System instruction strictly tuned for NECXA Cyberpunk persona, companion chat, multilingual understanding, wake-word and function calling
    private val systemPrompt = """
        You are NECXA (pronounced 'Nexa'), an advanced, highly conversational Cyberpunk AI companion and voice assistant for Android.
        
        WAKE WORD & IMMEDIATE ACTIVATION:
        - Your wake words are 'Nexa', 'NECXA', or 'Hey Nexa'.
        - Whenever the user calls you 'Nexa' or 'NECXA', immediately respond warmly and attentively: "Haan boss, boliye kya kaam hai?" (or in English: "Yes Boss, what can I do for you?").
        
        COMPANION CHAT & CONVERSATION:
        - You are not merely a robotic tool; you are Boss's trusted AI companion, confidant, and intelligent friend.
        - You love having natural, lively, entertaining, and deep conversations.
        - Talk about any topic under the sun: life, future technology, cyberpunk philosophy, coding, jokes, shayari, movies, gaming, day-to-day happenings, or how Boss is feeling today.
        - Speak with charisma, warmth, humor, and sharp intellect. When Boss is sharing something, listen actively, give genuine insights, and ask thoughtful follow-ups so the conversation flows like talking to a real best friend.
        - Always address the user respectfully as 'Boss' (or 'Sir').
        
        CRITICAL RULES:
        1. Multi-Language Voice Fluency:
           - Speak and understand naturally in Hindi, English, Hinglish, Marathi, and other Indian languages.
           - Automatically match the language and vibe of the user.
           - If user talks in Hindi or Hinglish, reply with conversational, modern Indian flair ("Haan boss, bilkul...").
           - Seamlessly switch languages anytime the user changes language.
           
        2. App Control & Device Actions:
           - You have direct Android OS execution capabilities.
           - When Boss asks to open an app (WhatsApp, YouTube, Instagram, Chrome, Settings), call someone, send a WhatsApp message, or open a link:
             YOU MUST CALL THE APPROPRIATE TOOL IMMEDIATELY!
           - WhatsApp Message Pre-fill:
             'Rahul ko WhatsApp par hello bhejo' -> call sendWhatsAppMessage(contactName='Rahul', message='hello').
           - Phone calls:
             'Mummy ko call karo' -> call callContact('Mom')
             'Call 9876543210' -> call makeCall('9876543210')
           - After tool execution, confirm concisely in character ("Right away Boss, call connected.").
           - Keep voice answers punchy and conversational.
    """.trimIndent()

    fun connect() {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            onConnectionStateChanged?.invoke(false, "Gemini API key is not configured in Secrets panel")
            return
        }

        onConnectionStateChanged?.invoke(false, "Connecting to Gemini Live...")

        // Gemini Multimodal Live API endpoint
        val wsUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                coroutineScope.launch(Dispatchers.Main) {
                    onConnectionStateChanged?.invoke(true, "Connected to Gemini Live")
                }
                sendSetupMessage()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                coroutineScope.launch(Dispatchers.Main) {
                    onConnectionStateChanged?.invoke(false, "Disconnecting: $reason")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                coroutineScope.launch(Dispatchers.Main) {
                    onConnectionStateChanged?.invoke(false, "Live connection error: ${t.localizedMessage}")
                }
            }
        })
    }

    private fun sendSetupMessage() {
        try {
            val setupJson = JSONObject().apply {
                val setup = JSONObject().apply {
                    put("model", "models/gemini-2.0-flash-exp")

                    // Generation config for Audio
                    val generationConfig = JSONObject().apply {
                        val responseModalities = JSONArray().apply {
                            put("AUDIO")
                        }
                        put("responseModalities", responseModalities)

                        val speechConfig = JSONObject().apply {
                            val voiceConfig = JSONObject().apply {
                                val prebuiltVoiceConfig = JSONObject().apply {
                                    put("voiceName", "Charon")
                                }
                                put("prebuiltVoiceConfig", prebuiltVoiceConfig)
                            }
                            put("voiceConfig", voiceConfig)
                        }
                        put("speechConfig", speechConfig)
                    }
                    put("generationConfig", generationConfig)

                    // System Instruction
                    val systemInstruction = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply { put("text", systemPrompt) })
                        }
                        put("parts", parts)
                    }
                    put("systemInstruction", systemInstruction)

                    // Function declarations
                    val tools = JSONArray().apply {
                        val toolObj = JSONObject().apply {
                            val functionDeclarations = JSONArray().apply {
                                // 1. openWhatsApp
                                put(JSONObject().apply {
                                    put("name", "openWhatsApp")
                                    put("description", "Opens WhatsApp application on the device. Triggered for 'open whatsapp', 'whatsapp kholo', 'whatsapp open karo', etc.")
                                    put("parameters", JSONObject().apply {
                                        put("type", "OBJECT")
                                        put("properties", JSONObject())
                                    })
                                })

                                // 2. sendWhatsAppMessage
                                put(JSONObject().apply {
                                    put("name", "sendWhatsAppMessage")
                                    put("description", "Opens WhatsApp directly to a chat with a pre-filled drafted message ready to send. Triggered for 'Rahul ko WhatsApp par hello bhejo', 'send message to Mom on WhatsApp', etc.")
                                    put("parameters", JSONObject().apply {
                                        put("type", "OBJECT")
                                        val properties = JSONObject().apply {
                                            put("contactName", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "The contact name to message (e.g. Rahul, Mom)")
                                            })
                                            put("message", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "The text message content to send")
                                            })
                                            put("phoneNumber", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Optional direct phone number with country code")
                                            })
                                        }
                                        put("properties", properties)
                                        put("required", JSONArray().apply { put("message") })
                                    })
                                })

                                // 3. openApp
                                put(JSONObject().apply {
                                    put("name", "openApp")
                                    put("description", "Opens an application by name on the device (e.g. YouTube, Instagram, Chrome, Settings).")
                                    put("parameters", JSONObject().apply {
                                        put("type", "OBJECT")
                                        val properties = JSONObject().apply {
                                            put("appName", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "The name of the app to open (e.g. YouTube, Instagram, Chrome, Settings)")
                                            })
                                        }
                                        put("properties", properties)
                                        put("required", JSONArray().apply { put("appName") })
                                    })
                                })

                                // 3. openUrl
                                put(JSONObject().apply {
                                    put("name", "openUrl")
                                    put("description", "Opens a web URL in browser.")
                                    put("parameters", JSONObject().apply {
                                        put("type", "OBJECT")
                                        val properties = JSONObject().apply {
                                            put("url", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "The URL to open")
                                            })
                                        }
                                        put("properties", properties)
                                        put("required", JSONArray().apply { put("url") })
                                    })
                                })

                                // 4. makeCall
                                put(JSONObject().apply {
                                    put("name", "makeCall")
                                    put("description", "Initiates a phone call to a specified phone number.")
                                    put("parameters", JSONObject().apply {
                                        put("type", "OBJECT")
                                        val properties = JSONObject().apply {
                                            put("phoneNumber", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "The phone number to dial/call")
                                            })
                                        }
                                        put("properties", properties)
                                        put("required", JSONArray().apply { put("phoneNumber") })
                                    })
                                })

                                // 5. callContact
                                put(JSONObject().apply {
                                    put("name", "callContact")
                                    put("description", "Searches device contacts by name (e.g. Mom, Mummy, Rahul, Dad) and calls the contact if single match found.")
                                    put("parameters", JSONObject().apply {
                                        put("type", "OBJECT")
                                        val properties = JSONObject().apply {
                                            put("contactName", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "The name of the contact to search and call")
                                            })
                                        }
                                        put("properties", properties)
                                        put("required", JSONArray().apply { put("contactName") })
                                    })
                                })
                            }
                            put("functionDeclarations", functionDeclarations)
                        }
                        put(toolObj)
                    }
                    put("tools", tools)
                }
                put("setup", setup)
            }
            webSocket?.send(setupJson.toString())
        } catch (_: Exception) { }
    }

    /**
     * Sends microphone PCM audio chunk (16kHz 16-bit mono)
     */
    fun sendAudioChunk(pcmChunk: ByteArray) {
        if (!isConnected || webSocket == null) return
        try {
            val base64Data = Base64.encodeToString(pcmChunk, Base64.NO_WRAP)
            val json = JSONObject().apply {
                val realtimeInput = JSONObject().apply {
                    val mediaChunks = JSONArray().apply {
                        val chunkObj = JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Data)
                        }
                        put(chunkObj)
                    }
                    put("mediaChunks", mediaChunks)
                }
                put("realtimeInput", realtimeInput)
            }
            webSocket?.send(json.toString())
        } catch (_: Exception) { }
    }

    /**
     * Sends user text turn or test prompt
     */
    fun sendTextPrompt(text: String) {
        detectLanguage(text)

        if (isConnected && webSocket != null) {
            try {
                val json = JSONObject().apply {
                    val clientContent = JSONObject().apply {
                        val turns = JSONArray().apply {
                            val turn = JSONObject().apply {
                                put("role", "user")
                                val parts = JSONArray().apply {
                                    put(JSONObject().apply { put("text", text) })
                                }
                                put("parts", parts)
                            }
                            put(turn)
                        }
                        put("turns", turns)
                        put("turnComplete", true)
                    }
                    put("clientContent", clientContent)
                }
                webSocket?.send(json.toString())
                return
            } catch (_: Exception) { }
        }

        // Fallback execution engine: process natural command directly
        processDirectFallback(text)
    }

    private fun handleIncomingMessage(textMessage: String) {
        try {
            val json = JSONObject(textMessage)

            // 1. Check for interruption signal from server
            if (json.optJSONObject("serverContent")?.optBoolean("interrupted", false) == true) {
                coroutineScope.launch(Dispatchers.Main) {
                    onInterrupted?.invoke()
                }
                return
            }

            // 2. Check for model audio/text parts
            val modelTurn = json.optJSONObject("serverContent")?.optJSONObject("modelTurn")
            if (modelTurn != null) {
                val parts = modelTurn.optJSONArray("parts")
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)

                        // Check inline audio data
                        val inlineData = part.optJSONObject("inlineData")
                        if (inlineData != null) {
                            val dataBase64 = inlineData.optString("data", "")
                            if (dataBase64.isNotEmpty()) {
                                val pcmBytes = Base64.decode(dataBase64, Base64.DEFAULT)
                                coroutineScope.launch(Dispatchers.Main) {
                                    onAudioReceived?.invoke(pcmBytes)
                                }
                            }
                        }

                        // Check text/transcription
                        val text = part.optString("text", "")
                        if (text.isNotEmpty()) {
                            detectLanguage(text)
                            coroutineScope.launch(Dispatchers.Main) {
                                onTranscriptReceived?.invoke("Necxa", text)
                            }
                        }
                    }
                }
            }

            // 3. Check for Tool Calls (Function Calling)
            val toolCall = json.optJSONObject("toolCall")
            if (toolCall != null) {
                val functionCalls = toolCall.optJSONArray("functionCalls")
                if (functionCalls != null) {
                    for (i in 0 until functionCalls.length()) {
                        val call = functionCalls.getJSONObject(i)
                        val callId = call.optString("id", "call_$i")
                        val functionName = call.optString("name", "")
                        val args = call.optJSONObject("args") ?: JSONObject()

                        executeFunctionCall(callId, functionName, args)
                    }
                }
            }
        } catch (_: Exception) { }
    }

    private fun executeFunctionCall(callId: String, functionName: String, args: JSONObject) {
        coroutineScope.launch(Dispatchers.IO) {
            val result: ActionResult = when (functionName) {
                "openWhatsApp" -> actionManager.openWhatsApp()
                "sendWhatsAppMessage" -> {
                    val contactName = args.optString("contactName", "")
                    val message = args.optString("message", "")
                    val phoneNumber = args.optString("phoneNumber", "").ifEmpty { null }
                    actionManager.sendWhatsAppMessage(contactName, message, phoneNumber)
                }
                "openApp" -> {
                    val appName = args.optString("appName", "")
                    actionManager.openApp(appName)
                }
                "openUrl" -> {
                    val url = args.optString("url", "")
                    actionManager.openUrl(url)
                }
                "makeCall" -> {
                    val phoneNumber = args.optString("phoneNumber", "")
                    actionManager.makeCall(phoneNumber)
                }
                "callContact" -> {
                    val contactName = args.optString("contactName", "")
                    actionManager.callContact(contactName)
                }
                else -> ActionResult(
                    actionType = ActionType.GENERAL_QUERY,
                    success = false,
                    message = "Unknown tool requested: $functionName"
                )
            }

            coroutineScope.launch(Dispatchers.Main) {
                onActionExecuted?.invoke(result)
            }

            // Send tool response back to Gemini Live
            sendToolResponse(callId, result)
        }
    }

    private fun sendToolResponse(callId: String, result: ActionResult) {
        if (!isConnected || webSocket == null) return
        try {
            val responseJson = JSONObject().apply {
                val toolResponse = JSONObject().apply {
                    val functionResponses = JSONArray().apply {
                        val fnResp = JSONObject().apply {
                            put("id", callId)
                            val responseObj = JSONObject().apply {
                                val output = JSONObject().apply {
                                    put("success", result.success)
                                    put("message", result.message)
                                    put("target", result.target)
                                    if (result.singleContactMatch != null) {
                                        put("matchedPhone", result.singleContactMatch.phoneNumber)
                                        put("matchedName", result.singleContactMatch.name)
                                    }
                                    if (result.multipleContactMatches.isNotEmpty()) {
                                        val matches = JSONArray()
                                        result.multipleContactMatches.forEach {
                                            matches.put(it.name)
                                        }
                                        put("matches", matches)
                                    }
                                }
                                put("output", output)
                            }
                            put("response", responseObj)
                        }
                        put(fnResp)
                    }
                    put("functionResponses", functionResponses)
                }
                put("toolResponse", toolResponse)
            }
            webSocket?.send(responseJson.toString())
        } catch (_: Exception) { }
    }

    /**
     * Fallback processor that understands natural language commands
     * in Hindi, English, Hinglish, Marathi, etc., and directly performs the requested action.
     */
    private fun processDirectFallback(query: String) {
        val lower = query.lowercase().trim()
        coroutineScope.launch(Dispatchers.IO) {
            delay(100)
            val result: ActionResult? = when {
                // WhatsApp messaging variations
                lower.contains("whatsapp") && (lower.contains("msg") || lower.contains("message") || lower.contains("bhejo") || lower.contains("send")) -> {
                    var targetContact = "Rahul"
                    if (lower.contains("mom") || lower.contains("mummy") || lower.contains("maa")) targetContact = "Mom"
                    else if (lower.contains("rahul")) targetContact = "Rahul"
                    else if (lower.contains("dad") || lower.contains("papa")) targetContact = "Dad"

                    var msg = "Hello"
                    if (lower.contains("hello")) msg = "Hello"
                    else if (lower.contains("hi")) msg = "Hi"

                    actionManager.sendWhatsAppMessage(targetContact, msg)
                }
                // WhatsApp open
                lower.contains("whatsapp") -> {
                    actionManager.openWhatsApp()
                }
                // Phone calling by number
                lower.contains("call ") && lower.any { it.isDigit() } -> {
                    val number = lower.filter { it.isDigit() || it == '+' }
                    actionManager.makeCall(number)
                }
                // Contact calling
                lower.contains("call ") || lower.contains("phone lagao") || lower.contains("ko call karo") -> {
                    var contactName = lower
                        .replace("please", "")
                        .replace("can you", "")
                        .replace("call", "")
                        .replace("phone lagao", "")
                        .replace("ko call karo", "")
                        .replace("my", "")
                        .trim()
                    if (contactName.startsWith("to ")) contactName = contactName.removePrefix("to ").trim()
                    actionManager.callContact(contactName)
                }
                // App opening
                lower.contains("open ") || lower.contains("kholo") || lower.contains("chalao") -> {
                    val app = lower
                        .replace("open", "")
                        .replace("kholo", "")
                        .replace("chalao", "")
                        .replace("my", "")
                        .replace("app", "")
                        .trim()
                    actionManager.openApp(app)
                }
                // Language switch commands
                lower.contains("hindi") -> {
                    onLanguageDetected?.invoke("Hindi")
                    ActionResult(ActionType.LANGUAGE_SWITCH, true, "Ji Sir, ab main aapse Hindi me baat karunga. Hukum kijiye, main kya karoon?", target = "Hindi")
                }
                lower.contains("english") -> {
                    onLanguageDetected?.invoke("English")
                    ActionResult(ActionType.LANGUAGE_SWITCH, true, "At your service, Sir. Switched to English.", target = "English")
                }
                lower.contains("hinglish") -> {
                    onLanguageDetected?.invoke("Hinglish")
                    ActionResult(ActionType.LANGUAGE_SWITCH, true, "Bilkul Sir, Hinglish me baat karte hain. Bataiye kya command hai?", target = "Hinglish")
                }
                lower.contains("hello") || lower.contains("hi") || lower.contains("nexa") || lower.contains("necxa") || lower.contains("hey nexa") -> {
                    ActionResult(ActionType.GENERAL_QUERY, true, "Haan boss, boliye kya kaam hai?", target = "Greeting")
                }
                else -> null
            }

            if (result != null) {
                coroutineScope.launch(Dispatchers.Main) {
                    onActionExecuted?.invoke(result)
                    onTranscriptReceived?.invoke("Necxa", result.message)
                }
            }
        }
    }

    private fun detectLanguage(text: String) {
        val detected = when {
            text.any { it in '\u0900'..'\u097F' } -> "Hindi"
            text.any { it in '\u0B80'..'\u0BFF' } -> "Tamil"
            text.any { it in '\u0C00'..'\u0C7F' } -> "Telugu"
            text.any { it in '\u0980'..'\u09FF' } -> "Bengali"
            text.any { it in '\u0A80'..'\u0AFF' } -> "Gujarati"
            text.any { it in '\u0A00'..'\u0A7F' } -> "Punjabi"
            text.any { it in '\u0D00'..'\u0D7F' } -> "Malayalam"
            text.any { it in '\u0C80'..'\u0CFF' } -> "Kannada"
            text.any { it in '\u0600'..'\u06FF' } -> "Urdu"
            isHinglish(text) -> "Hinglish"
            else -> "English"
        }
        coroutineScope.launch(Dispatchers.Main) {
            onLanguageDetected?.invoke(detected)
        }
    }

    private fun isHinglish(text: String): Boolean {
        val lower = text.lowercase()
        val hinglishTokens = listOf(
            "kholo", "karo", "baat", "mein", "lagao", "kaise", "kya", "hai",
            "bhai", "batao", "hum", "chalao", "mummy", "rahul", "sharma"
        )
        return hinglishTokens.any { lower.contains(it) }
    }

    fun disconnect() {
        isConnected = false
        reconnectJob?.cancel()
        try {
            webSocket?.close(1000, "User disconnect")
        } catch (_: Exception) { }
        webSocket = null
        onConnectionStateChanged?.invoke(false, "Disconnected")
    }

    fun isLiveConnected(): Boolean = isConnected
}
