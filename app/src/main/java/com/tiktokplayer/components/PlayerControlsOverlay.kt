package com.tiktokplayer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Portrait
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiktokplayer.ui.theme.AccentRed
import com.tiktokplayer.ui.theme.ControlBackground
import com.tiktokplayer.ui.theme.White
import com.tiktokplayer.ui.theme.White50
import com.tiktokplayer.ui.theme.White70
import com.tiktokplayer.viewmodel.PlaybackState
import com.tiktokplayer.viewmodel.TimerState

@Composable
fun PlayerControlsOverlay(
    playbackState: PlaybackState,
    timerState: TimerState,
    videoName: String,
    isLandscape: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onToggleLandscape: () -> Unit,
    onSetTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }

    Box(modifier = modifier.fillMaxSize()) {
        // Top gradient + info bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
                .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Video name
                Text(
                    text = videoName,
                    color = White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                // Landscape toggle
                IconButton(onClick = onToggleLandscape) {
                    Icon(
                        imageVector = if (isLandscape) Icons.Default.Portrait else Icons.Default.Landscape,
                        contentDescription = if (isLandscape) "竖屏" else "横屏",
                        tint = White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Center play/pause
        IconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier
                .align(Alignment.Center)
                .size(72.dp)
                .clip(CircleShape)
                .background(ControlBackground)
        ) {
            Icon(
                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playbackState.isPlaying) "暂停" else "播放",
                tint = White,
                modifier = Modifier.size(48.dp)
            )
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp, top = 24.dp)
        ) {
            // Progress bar
            val progress = if (playbackState.totalDuration > 0) {
                (if (isSeeking) seekPosition else playbackState.currentPosition.toFloat()) / playbackState.totalDuration.toFloat()
            } else 0f

            Slider(
                value = progress,
                onValueChange = { newProgress ->
                    isSeeking = true
                    seekPosition = newProgress * playbackState.totalDuration
                },
                onValueChangeFinished = {
                    isSeeking = false
                    onSeek(seekPosition.toLong())
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = AccentRed,
                    activeTrackColor = AccentRed,
                    inactiveTrackColor = White30
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Time + controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time display
                Text(
                    text = "${formatTime(if (isSeeking) seekPosition.toLong() else playbackState.currentPosition)} / ${formatTime(playbackState.totalDuration)}",
                    color = White70,
                    fontSize = 13.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Speed control
                    Box {
                        TextButton(onClick = { showSpeedMenu = true }) {
                            Text(
                                text = "${playbackState.playbackSpeed}x",
                                color = AccentRed,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        DropdownMenu(
                            expanded = showSpeedMenu,
                            onDismissRequest = { showSpeedMenu = false },
                            modifier = Modifier.background(Color(0xFF2A2A2A))
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f).forEach { speed ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${speed}x",
                                            color = if (playbackState.playbackSpeed == speed) AccentRed else White,
                                            fontWeight = if (playbackState.playbackSpeed == speed) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        onSpeedChange(speed)
                                        showSpeedMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Timer
                    Box {
                        IconButton(onClick = { showTimerDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "定时关闭",
                                tint = if (timerState.isActive) AccentRed else White70,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Timer active indicator
                    if (timerState.isActive) {
                        Text(
                            text = formatTime(timerState.remainingMs),
                            color = AccentRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onCancelTimer) {
                            Icon(
                                imageVector = Icons.Default.TimerOff,
                                contentDescription = "取消定时",
                                tint = White50,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Timer dialog
    if (showTimerDialog) {
        TimerDialog(
            onDismiss = { showTimerDialog = false },
            onConfirm = { minutes ->
                onSetTimer(minutes)
                showTimerDialog = false
            },
            onCancelTimer = {
                onCancelTimer()
                showTimerDialog = false
            },
            isTimerActive = timerState.isActive
        )
    }
}

@Composable
private fun TimerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    isTimerActive: Boolean
) {
    val timerOptions = listOf(
        "5分钟" to 5,
        "10分钟" to 10,
        "15分钟" to 15,
        "30分钟" to 30,
        "45分钟" to 45,
        "60分钟" to 60,
        "90分钟" to 90,
        "120分钟" to 120
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = White,
        textContentColor = White70,
        title = { Text("定时关闭") },
        text = {
            Column {
                if (isTimerActive) {
                    TextButton(onClick = onCancelTimer) {
                        Text("取消当前定时", color = AccentRed)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                timerOptions.forEach { (label, minutes) ->
                    TextButton(onClick = { onConfirm(minutes) }) {
                        Text(label, color = White, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = White50)
            }
        }
    )
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}


