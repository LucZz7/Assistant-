package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.service.NecxaVoiceService
import com.example.ui.components.ActionFeedbackCard
import com.example.ui.components.NecxaOrbVisualizer
import com.example.ui.components.TranscriptView
import com.example.ui.theme.JarvisGlassBorder
import com.example.ui.theme.JarvisGlassSurface
import com.example.ui.theme.JarvisObsidian
import com.example.ui.theme.JarvisRed
import com.example.ui.theme.JarvisRedDark
import com.example.ui.theme.JarvisRedGlassBorder
import com.example.ui.theme.JarvisRedNeon
import com.example.ui.theme.JarvisWhite
import com.example.ui.theme.JarvisWhiteDim
import com.example.ui.theme.JarvisWhiteMuted

@Composable
fun NecxaApp(
    viewModel: NecxaViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var textInput by remember { mutableStateOf("") }

    // Cyberpunk scanline animation
    val infiniteTransition = rememberInfiniteTransition(label = "cyberpunk_scanline")
    val scanlineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanline_beam"
    )

    // Multi-permission launcher for Audio, Contacts, Phone Calls, and Notifications
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val contactsGranted = permissions[Manifest.permission.READ_CONTACTS] ?: false
        val callGranted = permissions[Manifest.permission.CALL_PHONE] ?: false
        viewModel.updatePermissions(audioGranted, contactsGranted, callGranted)

        if (audioGranted) {
            NecxaVoiceService.startService(context)
        }
    }

    LaunchedEffect(Unit) {
        val audio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val contacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val call = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

        viewModel.updatePermissions(audio, contacts, call)

        val needed = mutableListOf<String>()
        if (!audio) needed.add(Manifest.permission.RECORD_AUDIO)
        if (!contacts) needed.add(Manifest.permission.READ_CONTACTS)
        if (!call) needed.add(Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            NecxaVoiceService.startService(context)
        }
    }

    // Function to safely redirect to Telegram chat @i_necxy
    fun openTelegramDeveloperChat() {
        val tgUri = Uri.parse("tg://resolve?domain=i_necxy")
        val webUri = Uri.parse("https://t.me/i_necxy")
        try {
            val appIntent = Intent(Intent.ACTION_VIEW, tgUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(appIntent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(webIntent)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisObsidian),
        containerColor = JarvisObsidian
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .drawBehind {
                    // 1. Cyberpunk Crimson & Neon Cyan Ambient Radial Glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF0055).copy(alpha = 0.16f),
                                Color(0xFF00F0FF).copy(alpha = 0.04f),
                                Color(0xFF05050A).copy(alpha = 0.02f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = size.width * 0.85f
                        )
                    )

                    // 2. Subtle Animated Cyberpunk Scanline Beam
                    val beamY = size.height * scanlineY
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF00F0FF).copy(alpha = 0.12f),
                                Color(0xFFFF0055).copy(alpha = 0.18f),
                                Color(0xFF00F0FF).copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        ),
                        start = Offset(0f, beamY),
                        end = Offset(size.width, beamY),
                        strokeWidth = 2.5f
                    )
                }
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // --- TOP HEADER: Centered NECXA Title (Clean, pure, no clutter underneath) ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NECXA",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = JarvisWhite,
                        letterSpacing = 6.sp,
                        modifier = Modifier.testTag("app_title")
                    )
                }

                // --- CENTER: Cyberpunk Arc Reactor Visualizer ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable {
                            if (uiState.status == AssistantStatus.SPEAKING) {
                                viewModel.interruptAssistant()
                            } else {
                                viewModel.toggleMic()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    NecxaOrbVisualizer(
                        status = uiState.status,
                        audioLevel = uiState.audioLevel,
                        modifier = Modifier.testTag("necxa_orb_visualizer")
                    )
                }

                // --- Action Feedback Card (Appears only when an action is executed) ---
                ActionFeedbackCard(
                    actionResult = uiState.lastActionResult,
                    onContactSelected = { contact ->
                        viewModel.actionManager.makeCall(contact.phoneNumber)
                    }
                )

                // --- Live Transcript Conversation Stream ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                ) {
                    TranscriptView(messages = uiState.messages)
                }

                // --- Bottom Sleek Cyberpunk Command & Voice Bar ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Glass Cyber Text Input
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = {
                            Text(
                                text = "Speak or message NECXA...",
                                fontSize = 13.sp,
                                color = JarvisWhiteMuted
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("command_text_input"),
                        shape = RoundedCornerShape(26.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = JarvisGlassSurface,
                            unfocusedContainerColor = JarvisGlassSurface,
                            focusedBorderColor = Color(0xFF00F0FF),
                            unfocusedBorderColor = JarvisGlassBorder,
                            focusedTextColor = JarvisWhite,
                            unfocusedTextColor = JarvisWhite
                        ),
                        maxLines = 1,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (textInput.isNotBlank()) {
                                viewModel.sendTextCommand(textInput)
                                textInput = ""
                            }
                        }),
                        trailingIcon = {
                            if (textInput.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        viewModel.sendTextCommand(textInput)
                                        textInput = ""
                                    },
                                    modifier = Modifier.testTag("send_command_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = JarvisRedNeon
                                    )
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    // Interrupt Button if NECXA is actively speaking
                    if (uiState.status == AssistantStatus.SPEAKING) {
                        IconButton(
                            onClick = { viewModel.interruptAssistant() },
                            modifier = Modifier
                                .size(50.dp)
                                .background(JarvisRedDark, CircleShape)
                                .border(1.5.dp, JarvisWhite, CircleShape)
                                .testTag("interrupt_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Interrupt",
                                tint = JarvisWhite,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Floating Glowing Crimson-Cyan Mic Button
                    FloatingActionButton(
                        onClick = { viewModel.toggleMic() },
                        containerColor = if (uiState.isMicActive) Color(0xFFFF0055) else JarvisRed,
                        contentColor = JarvisWhite,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(52.dp)
                            .border(
                                1.5.dp,
                                if (uiState.isMicActive) Color(0xFF00F0FF) else JarvisRedGlassBorder,
                                CircleShape
                            )
                            .testTag("mic_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isMicActive) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = if (uiState.isMicActive) "Mute" else "Speak",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // --- FOOTER: Subtle "NECXY DEVELOPER" with Telegram Redirect (@i_necxy) ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clickable { openTelegramDeveloperChat() }
                        .testTag("footer_developer_credit"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NECXY DEVELOPER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        color = JarvisWhiteDim.copy(alpha = 0.45f),
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}
