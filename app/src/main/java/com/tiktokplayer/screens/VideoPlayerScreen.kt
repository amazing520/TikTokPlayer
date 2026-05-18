package com.tiktokplayer.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tiktokplayer.components.PlayerControlsOverlay
import com.tiktokplayer.viewmodel.VideoPlayerViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoPlayerScreen(viewModel: VideoPlayerViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    val videoList by viewModel.videoList.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val timerState by viewModel.timerState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLandscape by viewModel.isLandscape.collectAsState()
    val showControls by viewModel.showControls.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val shouldClose by viewModel.shouldClose.collectAsState()

    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var initialized by remember { mutableStateOf(false) }

    // Initialize player
    LaunchedEffect(Unit) {
        if (!initialized) {
            viewModel.initializePlayer(context)
            initialized = true
        }
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

    // Keep screen on while playing
    DisposableEffect(playbackState.isPlaying) {
        if (playbackState.isPlaying) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Lifecycle observer for pause/resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.exoPlayer?.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Don't auto-resume, let user tap to play
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Set up player view when player is ready
    LaunchedEffect(viewModel.exoPlayer) {
        viewModel.exoPlayer?.let { player ->
            playerView?.player = player
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
                // Vertical pager for swiping between videos
                val pagerState = rememberPagerState(
                    initialPage = 0,
                    pageCount = { videoList.size }
                )

                // Sync pager with current index
                LaunchedEffect(currentIndex) {
                    if (pagerState.currentPage != currentIndex) {
                        pagerState.animateScrollToPage(currentIndex)
                    }
                }

                // Sync current index with pager
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.currentPage }.collect { page ->
                        if (page != currentIndex) {
                            viewModel.playVideoAtIndex(page)
                        }
                    }
                }

                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondBoundsPageCount = 1
                ) { page ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        // ExoPlayer View
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                                    visibility = View.VISIBLE

                                    // Ensure proper layout
                                    layoutParams = FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.MATCH_PARENT
                                    )

                                    playerView = this
                                    viewModel.exoPlayer?.let { player ->
                                        this.player = player
                                    }
                                }
                            },
                            update = { view ->
                                viewModel.exoPlayer?.let { player ->
                                    if (view.player !== player) {
                                        view.player = player
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Player controls overlay
                        if (showControls && page == pagerState.currentPage) {
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
        }
    }
}
