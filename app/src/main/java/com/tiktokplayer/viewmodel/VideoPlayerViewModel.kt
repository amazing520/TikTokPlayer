package com.tiktokplayer.viewmodel

import android.content.Context
import android.os.CountDownTimer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.tiktokplayer.data.MediaStoreRepository
import com.tiktokplayer.data.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val totalDuration: Long = 0L,
    val playbackSpeed: Float = 1.0f
)

data class TimerState(
    val isActive: Boolean = false,
    val remainingMs: Long = 0L,
    val totalMs: Long = 0L
)

class VideoPlayerViewModel(
    private val repository: MediaStoreRepository
) : ViewModel() {

    private val _videoList = MutableStateFlow<List<VideoItem>>(emptyList())
    val videoList: StateFlow<List<VideoItem>> = _videoList.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _timerState = MutableStateFlow(TimerState())
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLandscape = MutableStateFlow(false)
    val isLandscape: StateFlow<Boolean> = _isLandscape.asStateFlow()

    private val _showControls = MutableStateFlow(true)
    val showControls: StateFlow<Boolean> = _showControls.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _shouldClose = MutableStateFlow(false)
    val shouldClose: StateFlow<Boolean> = _shouldClose.asStateFlow()

    // Long-press speed boost
    private val _isLongPressSpeed = MutableStateFlow(false)
    val isLongPressSpeed: StateFlow<Boolean> = _isLongPressSpeed.asStateFlow()
    private var speedBeforeLongPress = 1.0f

    // Double-tap seek feedback
    private val _seekFeedback = MutableStateFlow<String?>(null)
    val seekFeedback: StateFlow<String?> = _seekFeedback.asStateFlow()
    private var seekFeedbackDismissJob: kotlinx.coroutines.Job? = null

    var exoPlayer: ExoPlayer? = null
        private set

    private var countDownTimer: CountDownTimer? = null
    private var progressUpdateTimer: CountDownTimer? = null
    private var lastPlayedIndex = -1

    init {
        loadVideos()
    }

    private fun loadVideos() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Load first batch for fast startup
                val firstBatch = repository.getVideosPaged(limit = PAGE_SIZE, offset = 0)
                if (firstBatch.isEmpty()) {
                    _errorMessage.value = "未找到本地视频文件"
                    _isLoading.value = false
                    return@launch
                }
                _videoList.value = firstBatch.shuffled()
                _isLoading.value = false

                // Load remaining videos in background
                val totalCount = repository.getVideoCount()
                if (totalCount > PAGE_SIZE) {
                    val remaining = repository.getVideosPaged(
                        limit = totalCount - PAGE_SIZE,
                        offset = PAGE_SIZE
                    )
                    val currentList = _videoList.value
                    _videoList.value = (currentList + remaining.shuffled())
                }
            } catch (e: Exception) {
                _errorMessage.value = "加载视频失败: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    companion object {
        private const val PAGE_SIZE = 50
    }

    fun initializePlayer(context: Context) {
        if (exoPlayer != null) return

        exoPlayer = ExoPlayer.Builder(context)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updatePlaybackState()
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            updatePlaybackState()
                        }
                    }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        updatePlaybackState()
                    }
                })
            }

        // Load first video
        if (_videoList.value.isNotEmpty()) {
            playVideoAtIndex(0)
            startProgressUpdater()
        }
    }

    fun playVideoAtIndex(index: Int) {
        val videos = _videoList.value
        if (videos.isEmpty() || index !in videos.indices) return
        if (index == lastPlayedIndex) return // Avoid redundant loads

        lastPlayedIndex = index
        _currentIndex.value = index
        exoPlayer?.apply {
            val currentMediaItem = MediaItem.fromUri(videos[index].uri)

            // Build playlist with current + next video for preloading
            val mediaItems = mutableListOf(currentMediaItem)
            val nextIndex = (index + 1) % videos.size
            if (nextIndex != index) {
                mediaItems.add(MediaItem.fromUri(videos[nextIndex].uri))
            }

            setMediaItems(mediaItems, 0, 0L)
            prepare()
            playWhenReady = true
            playbackParameters = PlaybackParameters(_playbackState.value.playbackSpeed)
        }
        updatePlaybackState()
    }

    fun togglePlayPause() {
        exoPlayer?.let {
            if (it.isPlaying) it.pause() else it.play()
            updatePlaybackState()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        updatePlaybackState()
    }

    fun seekForward(ms: Long = 10_000) {
        exoPlayer?.let {
            val target = (it.currentPosition + ms).coerceAtMost(it.duration)
            it.seekTo(target)
            updatePlaybackState()
            showSeekFeedback("+${ms / 1000}s")
        }
    }

    fun seekBackward(ms: Long = 10_000) {
        exoPlayer?.let {
            val target = (it.currentPosition - ms).coerceAtLeast(0)
            it.seekTo(target)
            updatePlaybackState()
            showSeekFeedback("-${ms / 1000}s")
        }
    }

    /**
     * Double-tap seek: left half = -10s, right half = +10s
     */
    fun doubleTapSeek(isRightHalf: Boolean) {
        if (isRightHalf) seekForward(10_000) else seekBackward(10_000)
    }

    private fun showSeekFeedback(text: String) {
        seekFeedbackDismissJob?.cancel()
        _seekFeedback.value = text
        seekFeedbackDismissJob = viewModelScope.launch {
            delay(800)
            _seekFeedback.value = null
        }
    }

    /**
     * Start long-press 3x speed (called on pointer down with hold)
     */
    fun startLongPressSpeed() {
        if (_isLongPressSpeed.value) return
        speedBeforeLongPress = _playbackState.value.playbackSpeed
        _isLongPressSpeed.value = true
        exoPlayer?.playbackParameters = PlaybackParameters(3.0f)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = 3.0f)
    }

    /**
     * End long-press speed (restore previous speed)
     */
    fun endLongPressSpeed() {
        if (!_isLongPressSpeed.value) return
        _isLongPressSpeed.value = false
        exoPlayer?.playbackParameters = PlaybackParameters(speedBeforeLongPress)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = speedBeforeLongPress)
    }

    fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.5f, 3.0f)
        exoPlayer?.playbackParameters = PlaybackParameters(clampedSpeed)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = clampedSpeed)
    }

    fun toggleLandscape() {
        _isLandscape.value = !_isLandscape.value
    }

    fun toggleControls() {
        _showControls.value = !_showControls.value
    }

    fun hideControls() {
        _showControls.value = false
    }

    fun setTimer(durationMinutes: Int) {
        cancelTimer()
        val durationMs = durationMinutes * 60 * 1000L
        _timerState.value = TimerState(isActive = true, remainingMs = durationMs, totalMs = durationMs)

        countDownTimer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _timerState.value = _timerState.value.copy(remainingMs = millisUntilFinished)
            }
            override fun onFinish() {
                _timerState.value = TimerState()
                exoPlayer?.pause()
                _playbackState.value = _playbackState.value.copy(isPlaying = false)
                _shouldClose.value = true
            }
        }.start()
    }

    fun cancelTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
        _timerState.value = TimerState()
    }

    fun consumeCloseEvent() {
        _shouldClose.value = false
    }

    private fun updatePlaybackState() {
        exoPlayer?.let {
            _playbackState.value = PlaybackState(
                isPlaying = it.isPlaying,
                currentPosition = it.currentPosition.coerceAtLeast(0),
                totalDuration = it.duration.coerceAtLeast(0),
                playbackSpeed = it.playbackParameters.speed
            )
        }
    }

    private fun startProgressUpdater() {
        progressUpdateTimer?.cancel()
        progressUpdateTimer = object : CountDownTimer(Long.MAX_VALUE, 500) {
            override fun onTick(millisUntilFinished: Long) {
                updatePlaybackState()
            }
            override fun onFinish() {}
        }.start()
    }

    override fun onCleared() {
        super.onCleared()
        progressUpdateTimer?.cancel()
        countDownTimer?.cancel()
        exoPlayer?.release()
        exoPlayer = null
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VideoPlayerViewModel(MediaStoreRepository(context)) as T
        }
    }
}
