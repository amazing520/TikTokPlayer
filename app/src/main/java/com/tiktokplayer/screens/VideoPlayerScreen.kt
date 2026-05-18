package com.tiktokplayer.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.media3.ui.PlayerView
import com.tiktokplayer.R
import com.tiktokplayer.components.PlayerControlsOverlay
import com.tiktokplayer.viewmodel.VideoPlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoPlayerScreen(viewModel: VideoPlayerViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    val videoList by viewModel.videoList.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val timerState by viewModel.timerState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLandscape by viewModel.isLandscape.collectAsState()
    val showControls by viewModel.showControls.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val shouldClose by viewModel.shouldClose.collectAsState()

    // Initialize player once
    LaunchedEffect(Unit) {
        viewModel.initializePlayer(context)
    }

    // Handle landscape/portrait rotation
    LaunchedEffect(isLandscape) {
        activity?.requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // Handle close event (timer finished)
    LaunchedEffect(shouldClose) {
        if (shouldClose) {
            viewModel.consumeCloseEvent()
            activity?.finish()
        }
    }

    // Auto-hide controls after 3 seconds of inactivity
    LaunchedEffect(showControls, playbackState.isPlaying) {
        if (showControls && playbackState.isPlaying) {
            delay(3000)
            viewModel.hideControls()
        }
    }

    // Keep screen on while playing
    DisposableEffect(playbackState.isPlaying) {
        val window = activity?.window
        if (playbackState.isPlaying) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Apply brightness changes to window — use snapshotFlow to observe non-State property
    LaunchedEffect(Unit) {
        snapshotFlow { viewModel.currentBrightness }
            .distinctUntilChanged()
            .collect { brightness ->
                val window = activity?.window
                window?.let {
                    val layoutParams = it.attributes
                    layoutParams.screenBrightness = brightness
                    it.attributes = layoutParams
                }
            }
    }

    // Lifecycle observer — pause on ON_PAUSE, release on ON_DESTROY
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.exoPlayer?.pause()
                Lifecycle.Event.ON_RESUME -> {
                    // Don't auto-resume; user taps play manually
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp),
                    color = Color.White
                )
            }
            errorMessage != null -> {
                Text(
                    text = errorMessage ?: "未知错误",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            videoList.isEmpty() -> {
                Text(
                    text = "未找到本地视频",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            else -> {
                VideoPager(
                    viewModel = viewModel,
                    videoList = videoList,
                    playbackState = playbackState,
                    timerState = timerState,
                    isLandscape = isLandscape,
                    showControls = showControls,
                    activity = activity
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoPager(
    viewModel: VideoPlayerViewModel,
    videoList: List<com.tiktokplayer.data.VideoItem>,
    playbackState: com.tiktokplayer.viewmodel.PlaybackState,
    timerState: com.tiktokplayer.viewmodel.TimerState,
    isLandscape: Boolean,
    showControls: Boolean,
    activity: Activity?
) {
    val pagerState = rememberPagerState(pageCount = { videoList.size })

    // Sync pager when ViewModel changes video (e.g. timer end)
    val currentIndex by viewModel.currentIndex.collectAsState()
    LaunchedEffect(currentIndex) {
        if (pagerState.currentPage != currentIndex && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(currentIndex)
        }
    }

    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        val isCurrentPage = page == pagerState.currentPage

        // Load video when this page becomes current
        LaunchedEffect(isCurrentPage) {
            if (isCurrentPage) {
                viewModel.playVideoAtIndex(page)
            }
        }

        // Gesture state
        var isLongPressing by remember { mutableStateOf(false) }
        var dragType by remember { mutableStateOf(DragType.NONE) }
        var dragStartY by remember { mutableFloatStateOf(0f) }
        var dragCurrentValue by remember { mutableFloatStateOf(0f) }

        // Clean up long-press when this composable leaves
        DisposableEffect(Unit) {
            onDispose {
                if (isLongPressing) {
                    isLongPressing = false
                    viewModel.resetLongPress()
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                // Gesture handler: single tap, double tap, long press
                .pointerInput(isCurrentPage) {
                    if (!isCurrentPage) return@pointerInput
                    detectTapGestures(
                        onTap = {
                            viewModel.toggleControls()
                        },
                        onDoubleTap = { offset ->
                            val isRightHalf = offset.x > size.width / 2
                            viewModel.doubleTapSeek(isRightHalf)
                        },
                        onLongPress = {
                            isLongPressing = true
                            viewModel.startLongPressSpeed()
                        }
                    )
                }
                // Long-press release detector
                .pointerInput(isCurrentPage, isLongPressing) {
                    if (!isCurrentPage || !isLongPressing) return@pointerInput
                    awaitPointerEventScope {
                        while (isLongPressing) {
                            val event = awaitPointerEvent()
                            val allReleased = event.changes.all { !it.pressed }
                            if (allReleased) {
                                isLongPressing = false
                                viewModel.endLongPressSpeed()
                            }
                        }
                    }
                }
                // Vertical drag for brightness (left) / volume (right)
                // Uses direction detection: only activate after clear vertical intent,
                // preventing conflict with VerticalPager's page-swipe gesture
                .pointerInput(isCurrentPage) {
                    if (!isCurrentPage) return@pointerInput
                    detectVerticalDragGesturesWithDirection(
                        onDragStart = { offset ->
                            dragType = if (offset.x < size.width / 2) DragType.BRIGHTNESS else DragType.VOLUME
                            dragStartY = offset.y
                            dragCurrentValue = if (dragType == DragType.BRIGHTNESS) viewModel.currentBrightness else viewModel.currentVolume
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val sensitivity = 300f
                            val delta: Float = -dragAmount / sensitivity
                            dragCurrentValue = (dragCurrentValue + delta).coerceIn(0f, 1f)
                            if (dragType == DragType.BRIGHTNESS) {
                                viewModel.setBrightness(dragCurrentValue)
                            } else {
                                viewModel.setVolume(dragCurrentValue)
                            }
                        },
                        onDragEnd = {
                            dragType = DragType.NONE
                        },
                        onDragCancel = {
                            dragType = DragType.NONE
                        }
                    )
                }
        ) {
            // ExoPlayer View — inflate from XML with surface_type="texture_view"
            // TextureView is more compatible with Compose and avoids black screen on many devices
            AndroidView(
                factory = { ctx ->
                    val view = LayoutInflater.from(ctx).inflate(R.layout.exo_player_view, null)
                    view.findViewById<PlayerView>(R.id.player_view).apply {
                        setKeepContentOnPlayerReset(true)
                    }
                    view
                },
                update = { view ->
                    val playerView = view.findViewById<PlayerView>(R.id.player_view)
                    if (isCurrentPage) {
                        if (playerView.player !== viewModel.exoPlayer) {
                            playerView.player = viewModel.exoPlayer
                        }
                    } else {
                        if (playerView.player != null) {
                            playerView.player = null
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Brightness/Volume drag indicator
            if (dragType != DragType.NONE) {
                val icon = if (dragType == DragType.BRIGHTNESS) "☀" else "🔊"
                val label = if (dragType == DragType.BRIGHTNESS) "亮度" else "音量"
                val pct = (dragCurrentValue * 100).toInt()
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = icon,
                            fontSize = 28.sp
                        )
                        Text(
                            text = "$label $pct%",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Seek feedback overlay (+10s, -10s)
            val seekFeedback by viewModel.seekFeedback.collectAsState()
            seekFeedback?.let { feedback ->
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = feedback,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Long-press speed indicator
            val isLongPressSpeed by viewModel.isLongPressSpeed.collectAsState()
            if (isLongPressSpeed) {
                Text(
                    text = "2x",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 100.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // Video info overlay (bottom-left, always visible)
            val videoItem = videoList.getOrNull(page)
            videoItem?.let { video ->
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 80.dp)
                ) {
                    Text(
                        text = video.displayName,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Text(
                        text = "${formatFileSize(video.size)} · ${formatDuration(video.duration)}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }

            // Player controls overlay
            if (isCurrentPage && showControls) {
                PlayerControlsOverlay(
                    playbackState = playbackState,
                    timerState = timerState,
                    videoName = videoList.getOrNull(page)?.displayName ?: "",
                    isLandscape = isLandscape,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onSeek = { viewModel.seekTo(it) },
                    onSpeedChange = { viewModel.setPlaybackSpeed(it) },
                    onToggleLandscape = { viewModel.toggleLandscape() },
                    onSetTimer = { viewModel.setTimer(it) },
                    onCancelTimer = { viewModel.cancelTimer() },
                    onClose = { activity?.finish() }
                )
            }
        }
    }
}

private enum class DragType { NONE, BRIGHTNESS, VOLUME }

/**
 * Direction-aware vertical drag gesture detector.
 * Waits for the user to move their finger past a threshold before activating,
 * and only activates if the movement is predominantly vertical.
 * This prevents conflicts with VerticalPager's page-swipe gesture.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectVerticalDragGesturesWithDirection(
    onDragStart: (androidx.compose.ui.geometry.Offset) -> Unit,
    onVerticalDrag: (change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val directionThreshold = 10f
    awaitPointerEventScope {
        val down = awaitFirstDown(requireUnconsumed = false)
        var dragStarted = false
        val startOffset = down.position

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: continue

            if (!change.pressed) {
                // Finger lifted
                if (dragStarted) onDragEnd()
                break
            }

            val currentPosition = change.position
            val dx = kotlin.math.abs(currentPosition.x - startOffset.x)
            val dy = kotlin.math.abs(currentPosition.y - startOffset.y)

            if (!dragStarted) {
                if (dx > directionThreshold || dy > directionThreshold) {
                    if (dy > dx) {
                        dragStarted = true
                        onDragStart(startOffset)
                        change.consume()
                    } else {
                        // Horizontal intent — abort, let pager handle
                        break
                    }
                }
            } else {
                change.consume()
                onVerticalDrag(change, change.position.y - change.previousPosition.y)
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
