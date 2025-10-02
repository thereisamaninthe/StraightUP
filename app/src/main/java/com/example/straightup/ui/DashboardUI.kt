package com.example.straightup.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.straightup.models.PostureScore
import com.example.straightup.models.PostureLevel
import com.example.straightup.models.UserBehavior
import com.example.straightup.sensors.SessionStats
import kotlin.math.cos
import kotlin.math.sin

/**
 * Main dashboard UI displaying posture monitoring information
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardUI(
    postureScore: PostureScore,
    userBehavior: UserBehavior,
    sessionStats: SessionStats,
    isMonitoring: Boolean,
    onToggleMonitoring: () -> Unit,
    onShowSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with app title and monitoring toggle
        DashboardHeader(
            isMonitoring = isMonitoring,
            onToggleMonitoring = onToggleMonitoring,
            onShowSettings = onShowSettings
        )

        // Main posture score display
        PostureScoreCard(postureScore = postureScore)

        // Real-time metrics
        MetricsRow(postureScore = postureScore)

        // Session statistics
        SessionStatsCard(sessionStats = sessionStats)

        // User behavior insights
        BehaviorInsightsCard(userBehavior = userBehavior)

        // Recommendations
        RecommendationsCard(recommendations = postureScore.recommendations)
    }
}

@Composable
private fun DashboardHeader(
    isMonitoring: Boolean,
    onToggleMonitoring: () -> Unit,
    onShowSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "StraightUP",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isMonitoring) "Monitoring your posture" else "Monitoring paused",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isMonitoring) Color(0xFF4CAF50) else Color(0xFF757575)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = isMonitoring,
                onCheckedChange = { onToggleMonitoring() }
            )

            IconButton(onClick = onShowSettings) {
                Text("⚙️", fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun PostureScoreCard(postureScore: PostureScore) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            // Circular progress indicator
            CircularPostureScore(
                score = postureScore.overall,
                level = postureScore.level
            )
        }
    }
}

@Composable
private fun CircularPostureScore(
    score: Float,
    level: PostureLevel
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(200.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircularProgress(
                score = score,
                color = Color(level.color),
                strokeWidth = 12.dp.toPx()
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${score.toInt()}",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = Color(level.color)
            )
            Text(
                text = level.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = Color(level.color)
            )
        }
    }
}

private fun DrawScope.drawCircularProgress(
    score: Float,
    color: Color,
    strokeWidth: Float
) {
    val sweepAngle = (score / 100f) * 360f
    val startAngle = -90f

    // Background circle
    drawCircle(
        color = color.copy(alpha = 0.2f),
        radius = size.minDimension / 2 - strokeWidth / 2,
        style = Stroke(width = strokeWidth)
    )

    // Progress arc
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        style = Stroke(width = strokeWidth)
    )
}

@Composable
private fun MetricsRow(postureScore: PostureScore) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricCard(
            title = "Tilt",
            value = "${postureScore.tiltScore.toInt()}",
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            title = "Distance",
            value = "${postureScore.distanceScore.toInt()}",
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            title = "Position",
            value = "${postureScore.positionScore.toInt()}",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SessionStatsCard(sessionStats: SessionStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Session Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = "Average",
                    value = "${sessionStats.averageScore.toInt()}"
                )
                StatItem(
                    label = "Best",
                    value = "${sessionStats.bestScore.toInt()}"
                )
                StatItem(
                    label = "Measurements",
                    value = "${sessionStats.totalMeasurements}"
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Improvement trend indicator
            val trendIcon = when {
                sessionStats.improvementTrend > 0.1f -> "📈"
                sessionStats.improvementTrend < -0.1f -> "📉"
                else -> "➡️"
            }
            val trendText = when {
                sessionStats.improvementTrend > 0.1f -> "Improving"
                sessionStats.improvementTrend < -0.1f -> "Declining"
                else -> "Stable"
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = trendIcon, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Trend: $trendText",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BehaviorInsightsCard(userBehavior: UserBehavior) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Behavior Insights",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            InsightItem(
                icon = "⏱️",
                label = "Session Duration",
                value = "${userBehavior.sessionDuration} minutes"
            )

            InsightItem(
                icon = "📊",
                label = "Response Rate",
                value = "${(userBehavior.reminderResponseRate * 100).toInt()}%"
            )

            InsightItem(
                icon = "📈",
                label = "Daily Usage",
                value = "${userBehavior.dailyUsage} minutes"
            )
        }
    }
}

@Composable
private fun InsightItem(
    icon: String,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun RecommendationsCard(recommendations: List<String>) {
    if (recommendations.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "💡 Recommendations",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                recommendations.forEach { recommendation ->
                    Text(
                        text = "• $recommendation",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}
