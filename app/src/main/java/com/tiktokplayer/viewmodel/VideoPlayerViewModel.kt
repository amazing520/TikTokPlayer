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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

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

    var exoPlayer: ExoPlayer? = null
        private set

    private var countDownTimer: CountDownTimer? = null
    private var progressUpdateTimer: CountDownTimer? = null
    private var shuffledIndices = mutableListOf<Int>()

    init {
        loadVideos()
    }

    private fun loadVideos() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val videos = repository.getAllVideos()
                if (videos.isEmpty()) {
                    _errorMessage.value = "未找到本地视频文件"
                    _isLoading.value = false
                    return@launch
                }
                _videoList.value = videos
                shuffledIndices = generateShuffledIndices(videos.size)
                _isLoading.value = false
            } catch (e: Exception) {
                _errorMessage.value = "加载视频失败: ${e.message}"
                _isLoading.value = false
            }
        }
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
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
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
            playVideoAtIndex(shuffledIndices[0])
            startProgressUpdater()
        }
    }

    private fun generateShuffledIndices(size: Int): MutableList<Int> {
        val indices = (0 until size).toMutableList()
        indices.shuffle(Random)
        return indices
    }

    fun playVideoAtIndex(index: Int) {
        val videos = _videoList.value
        if (videos.isEmpty() || index !in videos.indices) return

        _currentIndex.value = index
        exoPlayer?.apply {
            val mediaItem = MediaItem.fromUri(videos[index].uri)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
            playbackParameters = PlaybackParameters(_playbackState.value.playbackSpeed)
        }
        updatePlaybackState()
    }

    fun playNext() {
        val videos = _videoList.value
        if (videos.isEmpty()) return

        val currentShuffledPos = shuffledIndices.indexOf(_currentIndex.value)
        val nextShuffledPos = (currentShuffledPos + 1) % shuffledIndices.size
        playVideoAtIndex(shuffledIndices[nextShuffledPos])
    }

    fun playPrevious() {
        val videos = _videoList.value
        if (videos.isEmpty()) return

        val currentShuffledPos = shuffledIndices.indexOf(_currentIndex.value)
        val prevShuffledPos = if (currentShuffledPos <= 0) shuffledIndices.size - 1 else currentShuffledPos - 1
        playVideoAtIndex(shuffledIndices[prevShuffledPos])
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

    fun showControlsTemporarily() {
        _showControls.value = true
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
                // 触发关闭事件
                _shouldClose.value = true
            }
        }.start()
    }

    fun cancelTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
        _timerState.value = TimerState()
    }

    private val _shouldClose = MutableStateFlow(false)
    val shouldClose: StateFlow<Boolean> = _shouldClose.asStateFlow()

    fun consumeCloseEvent() {
        _shouldClose.value = false
    }

    fun formatTimerDisplay(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
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

    fun clearError() {
        _errorMessage.value = null
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
