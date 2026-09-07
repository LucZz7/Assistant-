package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActionResult
import com.example.data.model.ActionType
import com.example.data.model.ContactItem
import com.example.ui.theme.JarvisGlassBorder
import com.example.ui.theme.JarvisObsidian
import com.example.ui.theme.JarvisRed
import com.example.ui.theme.JarvisRedGlassBorder
import com.example.ui.theme.JarvisRedNeon
import com.example.ui.theme.JarvisWhite
import com.example.ui.theme.JarvisWhiteDim

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionFeedbackCard(
    actionResult: ActionResult?,
    onContactSelected: (ContactItem) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = actionResult != null,
        enter = slideInVertically(initialOffsetY = { -20 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -20 }) + fadeOut(),
        modifier = modifier
    ) {
        if (actionResult == null) return@AnimatedVisibility

        val icon = when (actionResult.actionType) {
            ActionType.OPEN_WHATSAPP -> Icons.Default.Smartphone
            ActionType.SEND_WHATSAPP_MESSAGE -> Icons.AutoMirrored.Filled.Send
            ActionType.OPEN_APP -> Icons.Default.Smartphone
            ActionType.OPEN_URL -> Icons.Default.OpenInBrowser
            ActionType.MAKE_CALL -> Icons.Default.Call
            ActionType.CALL_CONTACT -> Icons.Default.Phone
            ActionType.LANGUAGE_SWITCH -> Icons.Default.Language
            ActionType.GENERAL_QUERY -> Icons.Default.CheckCircle
        }

        val accentColor = if (actionResult.success) JarvisRedNeon else if (actionResult.multipleContactMatches.isNotEmpty()) Color(0xFFFFB300) else JarvisRed

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(JarvisRedGlassBorder, JarvisGlassBorder)
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .testTag("action_feedback_card"),
            color = JarvisObsidian.copy(alpha = 0.85f),
            shadowElevation = 12.dp
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(accentColor.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, accentColor.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = actionResult.actionType.name,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (actionResult.success) "NECXA // EXECUTED" else if (actionResult.multipleContactMatches.isNotEmpty()) "NECXA // CLARIFY TARGET" else "NECXA // NOTICE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = actionResult.message,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = JarvisWhite
                        )
                    }

                    Icon(
                        imageVector = if (actionResult.success) Icons.Default.CheckCircle else if (actionResult.multipleContactMatches.isNotEmpty()) Icons.Default.Warning else Icons.Default.Error,
                        contentDescription = "Status",
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // If multiple contacts matched, provide clean glass selection chips
                if (actionResult.multipleContactMatches.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Select contact to proceed:",
                        fontSize = 11.sp,
                        color = JarvisWhiteDim,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        actionResult.multipleContactMatches.forEach { contact ->
                            AssistChip(
                                onClick = { onContactSelected(contact) },
                                label = {
                                    Text(
                                        text = "${contact.name} (${contact.phoneNumber})",
                                        fontSize = 11.sp,
                                        color = JarvisWhite
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Call",
                                        tint = JarvisRedNeon,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color(0x22FFFFFF)
                                ),
                                border = AssistChipDefaults.assistChipBorder(
                                    enabled = true,
                                    borderColor = JarvisRedGlassBorder
                                ),
                                modifier = Modifier.testTag("contact_chip_${contact.name}")
                            )
                        }
                    }
                }
            }
        }
    }
}
