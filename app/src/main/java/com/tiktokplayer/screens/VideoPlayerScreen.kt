package com.tiktokplayer.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.exoplayer.ExoPlayer
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

    // When ViewModel changes video (e.g. timer end), sync pager
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

        // When this page becomes current, load its video
        LaunchedEffect(isCurrentPage) {
            if (isCurrentPage) {
                viewModel.playVideoAtIndex(page)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    viewModel.toggleControls()
                }
        ) {
            // ExoPlayer View - only attach player to current page
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
                        // Attach player to current page
                        if (view.player !== viewModel.exoPlayer) {
                            view.player = viewModel.exoPlayer
                        }
                    } else {
                        // Detach player from non-current pages
                        if (view.player != null) {
                            view.player = null
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Player controls overlay - only on current page
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
