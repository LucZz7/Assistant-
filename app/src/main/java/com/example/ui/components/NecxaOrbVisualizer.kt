package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.AssistantStatus
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cyberpunk HUD Core Reactor for NECXA
 * Features rotating holographic rings, neon cyan-crimson chromatic arcs,
 * audio-reactive energy pulses, and corner targeting reticles.
 */
@Composable
fun NecxaOrbVisualizer(
    status: AssistantStatus,
    audioLevel: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cyberpunk_reactor")

    // Dynamic breathing pulse
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.93f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cyber_pulse"
    )

    // Outer fast CW rotation for targeting ring
    val clockwiseRot by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rot_cw"
    )

    // Counter CCW rotation for inner quantum telemetry ring
    val counterRot by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rot_ccw"
    )

    // Hologram shockwave ripples
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cyber_wave"
    )

    // Cyberpunk Color Palette: Neon Crimson, Cyber Cyan, Matrix White, Deep Violet Black
    val cyberCrimson = Color(0xFFFF0055)
    val cyberCrimsonBright = Color(0xFFFF2A6D)
    val cyberCyan = Color(0xFF00F0FF)
    val cyberWhite = Color(0xFFFFFFFF)
    val cyberDark = Color(0xFF05050A)

    Box(
        modifier = modifier.size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(230.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.width * 0.28f
            val reactiveAmp = audioLevel.coerceIn(0f, 1f) * 35f

            // 1. Cyberpunk Corner HUD Targeting Brackets [ ]
            val bracketSize = 14f
            val bracketPad = 8f
            val bracketColor = cyberCyan.copy(alpha = 0.45f)
            val bracketStroke = Stroke(width = 1.5f)

            // Top-Left
            drawLine(bracketColor, Offset(bracketPad, bracketPad), Offset(bracketPad + bracketSize, bracketPad), strokeWidth = 1.5f)
            drawLine(bracketColor, Offset(bracketPad, bracketPad), Offset(bracketPad, bracketPad + bracketSize), strokeWidth = 1.5f)
            // Top-Right
            drawLine(bracketColor, Offset(size.width - bracketPad, bracketPad), Offset(size.width - bracketPad - bracketSize, bracketPad), strokeWidth = 1.5f)
            drawLine(bracketColor, Offset(size.width - bracketPad, bracketPad), Offset(size.width - bracketPad, bracketPad + bracketSize), strokeWidth = 1.5f)
            // Bottom-Left
            drawLine(bracketColor, Offset(bracketPad, size.height - bracketPad), Offset(bracketPad + bracketSize, size.height - bracketPad), strokeWidth = 1.5f)
            drawLine(bracketColor, Offset(bracketPad, size.height - bracketPad), Offset(bracketPad, size.height - bracketPad - bracketSize), strokeWidth = 1.5f)
            // Bottom-Right
            drawLine(bracketColor, Offset(size.width - bracketPad, size.height - bracketPad), Offset(size.width - bracketPad - bracketSize, size.height - bracketPad), strokeWidth = 1.5f)
            drawLine(bracketColor, Offset(size.width - bracketPad, size.height - bracketPad), Offset(size.width - bracketPad, size.height - bracketPad - bracketSize), strokeWidth = 1.5f)

            // 2. Diffuse Cyber Crimson & Cyan Holographic Radial Aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        cyberCrimson.copy(alpha = if (status == AssistantStatus.SPEAKING) 0.4f else 0.2f),
                        cyberCyan.copy(alpha = if (status == AssistantStatus.LISTENING) 0.25f else 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 2.4f + reactiveAmp
                ),
                radius = baseRadius * 2.4f + reactiveAmp,
                center = center
            )

            // 3. Audio-Reactive Cyber Shockwaves
            val waveCount = 3
            for (i in 0 until waveCount) {
                val progress = (waveOffset + i.toFloat() / waveCount) % 1f
                val ringRadius = baseRadius + (progress * 56f) + (reactiveAmp * 0.9f)
                val ringAlpha = (1f - progress) * (if (status == AssistantStatus.SPEAKING || status == AssistantStatus.LISTENING) 0.65f else 0.25f)

                drawCircle(
                    color = if (i % 2 == 0) cyberCrimson.copy(alpha = ringAlpha) else cyberCyan.copy(alpha = ringAlpha * 0.8f),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(
                        width = if (i % 2 == 0) 1.8f else 1.2f,
                        pathEffect = if (i % 2 == 0) PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f) else null
                    )
                )
            }

            // 4. Outer Rotating Cyber Telemetry Ring (with Neon Crimson & Cyan Segments)
            val hudRadius = baseRadius * 1.4f + (reactiveAmp * 0.35f)
            val arcSize = Size(hudRadius * 2f, hudRadius * 2f)
            val arcTopLeft = Offset(center.x - hudRadius, center.y - hudRadius)

            // Neon Crimson Segment
            drawArc(
                color = cyberCrimson.copy(alpha = 0.85f),
                startAngle = clockwiseRot,
                sweepAngle = 65f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = 2.8f, cap = StrokeCap.Round)
            )
            // Cyber Cyan Segment
            drawArc(
                color = cyberCyan.copy(alpha = 0.85f),
                startAngle = clockwiseRot + 120f,
                sweepAngle = 55f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = 2.4f, cap = StrokeCap.Round)
            )
            // Bright White Segment
            drawArc(
                color = cyberWhite.copy(alpha = 0.65f),
                startAngle = clockwiseRot + 230f,
                sweepAngle = 80f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = 2f, cap = StrokeCap.Round)
            )

            // 5. Degree Ticks around the HUD ring (Cyberpunk targeting reticle)
            val tickCount = 24
            for (t in 0 until tickCount) {
                val angleRad = Math.toRadians((clockwiseRot + t * (360f / tickCount)).toDouble())
                val isMajor = t % 6 == 0
                val tickLen = if (isMajor) 7f else 3.5f
                val r1 = hudRadius + 3f
                val r2 = r1 + tickLen
                val p1 = Offset(center.x + r1 * cos(angleRad).toFloat(), center.y + r1 * sin(angleRad).toFloat())
                val p2 = Offset(center.x + r2 * cos(angleRad).toFloat(), center.y + r2 * sin(angleRad).toFloat())

                drawLine(
                    color = if (isMajor) cyberCyan.copy(alpha = 0.8f) else cyberWhite.copy(alpha = 0.35f),
                    start = p1,
                    end = p2,
                    strokeWidth = if (isMajor) 1.5f else 1f
                )
            }

            // 6. Inner Counter-Rotating Hex/Arc Ring
            val innerHudRadius = baseRadius * 1.14f + (reactiveAmp * 0.2f)
            val innerArcSize = Size(innerHudRadius * 2f, innerHudRadius * 2f)
            val innerArcTopLeft = Offset(center.x - innerHudRadius, center.y - innerHudRadius)

            drawArc(
                color = cyberCyan.copy(alpha = 0.6f),
                startAngle = counterRot,
                sweepAngle = 45f,
                useCenter = false,
                topLeft = innerArcTopLeft,
                size = innerArcSize,
                style = Stroke(width = 1.6f, cap = StrokeCap.Round)
            )
            drawArc(
                color = cyberCrimsonBright.copy(alpha = 0.7f),
                startAngle = counterRot + 180f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = innerArcTopLeft,
                size = innerArcSize,
                style = Stroke(width = 2.2f, cap = StrokeCap.Round)
            )

            // 7. Orbiting Quantum Data Nodes (6 nodes)
            val nodeCount = 6
            for (i in 0 until nodeCount) {
                val angle = Math.toRadians((clockwiseRot + i * (360f / nodeCount)).toDouble())
                val nx = center.x + hudRadius * cos(angle).toFloat()
                val ny = center.y + hudRadius * sin(angle).toFloat()
                drawCircle(
                    color = if (i % 2 == 0) cyberCrimsonBright else cyberCyan,
                    radius = if (i % 2 == 0) 3.5f + (reactiveAmp * 0.08f) else 2.5f,
                    center = Offset(nx, ny)
                )
            }

            // 8. Cyberpunk Central Arc Reactor Core
            val dynamicRadius = (baseRadius * pulseScale) + reactiveAmp
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        cyberWhite,
                        cyberCrimsonBright.copy(alpha = 0.9f),
                        cyberCrimson.copy(alpha = 0.75f),
                        cyberDark
                    ),
                    center = center,
                    radius = dynamicRadius
                ),
                radius = dynamicRadius,
                center = center
            )

            // 9. Brilliant White-Hot Focal Center with Neon Cyan Tint
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        cyberWhite,
                        cyberCyan.copy(alpha = 0.8f),
                        cyberCrimson.copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = dynamicRadius * 0.42f
                ),
                radius = dynamicRadius * 0.42f,
                center = center
            )
        }
    }
}
