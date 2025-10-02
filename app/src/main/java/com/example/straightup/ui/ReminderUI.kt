package com.example.straightup.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.straightup.reminders.ReminderEvent
import com.example.straightup.reminders.ReminderLevel
import com.example.straightup.reminders.ReminderType
import com.example.straightup.reminders.UserAction

/**
 * UI component for displaying posture reminders
 */
@Composable
fun ReminderUI(
    reminderEvent: ReminderEvent?,
    onDismiss: () -> Unit,
    onUserAction: (UserAction) -> Unit,
    modifier: Modifier = Modifier
) {
    if (reminderEvent != null) {
        when (reminderEvent.type) {
            ReminderType.POPUP -> {
                PopupReminder(
                    event = reminderEvent,
                    onDismiss = onDismiss,
                    onUserAction = onUserAction
                )
            }
            ReminderType.OVERLAY -> {
                OverlayReminder(
                    event = reminderEvent,
                    onDismiss = onDismiss,
                    onUserAction = onUserAction
                )
            }
            ReminderType.NOTIFICATION -> {
                // Notification reminders would be handled by the system
                // This is just a placeholder for in-app notification display
                InAppNotificationReminder(
                    event = reminderEvent,
                    onDismiss = onDismiss,
                    modifier = modifier
                )
            }
            else -> {
                // Other reminder types don't need UI components
            }
        }
    }
}

@Composable
private fun PopupReminder(
    event: ReminderEvent,
    onDismiss: () -> Unit,
    onUserAction: (UserAction) -> Unit
) {
    Dialog(
        onDismissRequest = {
            onUserAction(UserAction.DISMISSED)
            onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        AnimatedReminderCard(
            event = event,
            onDismiss = onDismiss,
            onUserAction = onUserAction,
            isDialog = true
        )
    }
}

@Composable
private fun OverlayReminder(
    event: ReminderEvent,
    onDismiss: () -> Unit,
    onUserAction: (UserAction) -> Unit
) {
    // Full screen overlay for urgent reminders
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        AnimatedReminderCard(
            event = event,
            onDismiss = onDismiss,
            onUserAction = onUserAction,
            isDialog = false
        )
    }
}

@Composable
private fun InAppNotificationReminder(
    event: ReminderEvent,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(true) }

    LaunchedEffect(event) {
        // Auto-dismiss after 5 seconds
        kotlinx.coroutines.delay(5000)
        isVisible = false
        onDismiss()
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(300)
        ),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(300)
        ),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(event.level.color).copy(alpha = 0.9f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = getLevelIcon(event.level),
                    fontSize = 24.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )

                Text(
                    text = event.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )

                TextButton(
                    onClick = {
                        isVisible = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
private fun AnimatedReminderCard(
    event: ReminderEvent,
    onDismiss: () -> Unit,
    onUserAction: (UserAction) -> Unit,
    isDialog: Boolean
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(event) {
        isVisible = true
    }

    // Pulsing animation for urgent reminders
    val pulseAnimation = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseAnimation.animateFloat(
        initialValue = 1f,
        targetValue = if (event.level == ReminderLevel.URGENT) 0.7f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(),
        exit = scaleOut() + fadeOut()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(if (isDialog) 0.9f else 0.8f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(event.level.color).copy(alpha = pulseAlpha)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Level indicator
                Text(
                    text = getLevelIcon(event.level),
                    fontSize = 48.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Title
                Text(
                    text = event.level.displayName + " Reminder",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Message
                Text(
                    text = event.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Current posture score
                PostureScoreIndicator(
                    score = event.postureScore.overall,
                    level = event.postureScore.level
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                ReminderActionButtons(
                    level = event.level,
                    onUserAction = onUserAction,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun PostureScoreIndicator(
    score: Float,
    level: com.example.straightup.models.PostureLevel
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Current Score:",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f)
        )

        Text(
            text = "${score.toInt()}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Text(
            text = "(${level.displayName})",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun ReminderActionButtons(
    level: ReminderLevel,
    onUserAction: (UserAction) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Primary action - correct posture
        Button(
            onClick = {
                onUserAction(UserAction.CORRECTED_POSTURE)
                onDismiss()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color(level.color)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "✓ I'll Correct My Posture",
                fontWeight = FontWeight.Medium
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Postpone
            OutlinedButton(
                onClick = {
                    onUserAction(UserAction.POSTPONED)
                    onDismiss()
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("Postpone")
            }

            // Dismiss
            OutlinedButton(
                onClick = {
                    onUserAction(UserAction.DISMISSED)
                    onDismiss()
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("Dismiss")
            }
        }

        // Disable temporarily (only for non-critical reminders)
        if (level != ReminderLevel.URGENT) {
            TextButton(
                onClick = {
                    onUserAction(UserAction.DISABLED_TEMPORARILY)
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.White.copy(alpha = 0.7f)
                )
            ) {
                Text(
                    text = "Disable for 30 minutes",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun getLevelIcon(level: ReminderLevel): String {
    return when (level) {
        ReminderLevel.GENTLE -> "😊"
        ReminderLevel.MODERATE -> "🙂"
        ReminderLevel.STRONG -> "😐"
        ReminderLevel.URGENT -> "⚠️"
    }
}

/**
 * Floating reminder indicator for minimal interruption
 */
@Composable
fun FloatingReminderIndicator(
    isActive: Boolean,
    level: ReminderLevel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isActive,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier
    ) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = Color(level.color),
            contentColor = Color.White
        ) {
            Text(
                text = getLevelIcon(level),
                fontSize = 24.sp
            )
        }
    }
}
