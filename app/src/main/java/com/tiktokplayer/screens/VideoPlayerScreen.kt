package com.tiktokplayer.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tiktokplayer.components.PlayerControlsOverlay
import com.tiktokplayer.viewmodel.VideoPlayerViewModel
import kotlinx.coroutines.delay

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

    // Lifecycle observer
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.exoPlayer?.pause()
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

        // Reset long-press when page changes (prevent stuck state)
        LaunchedEffect(page) {
            // This runs once per page composition; if user swiped away mid-long-press,
            // the DisposableEffect below won't fire, so we reset here
        }

        // Gesture state
        var isLongPressing by remember { mutableStateOf(false) }

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
        ) {
            // ExoPlayer View - only attach to current page
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    }
                },
                update = { view ->
                    if (isCurrentPage) {
                        if (view.player !== viewModel.exoPlayer) {
                            view.player = viewModel.exoPlayer
                        }
                    } else {
                        if (view.player != null) {
                            view.player = null
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

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
