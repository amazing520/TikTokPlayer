package com.tiktokplayer.viewmodel

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
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
import org.json.JSONObject

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
    private var progressUpdateJob: kotlinx.coroutines.Job? = null
    private var lastPlayedIndex = -1

    // Brightness & Volume (0.0 ~ 1.0)
    var currentBrightness = 1f
        private set
    var currentVolume = 1f
        private set
    private var audioManager: AudioManager? = null

    // Playback history: videoId -> last position in ms
    private var playbackHistory = mutableMapOf<Long, Long>()

    init {
        loadVideos()
    }

    private fun loadVideos() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val firstBatch = repository.getVideosPaged(limit = PAGE_SIZE, offset = 0)
                if (firstBatch.isEmpty()) {
                    _errorMessage.value = "未找到本地视频文件"
                    _isLoading.value = false
                    return@launch
                }
                _videoList.value = firstBatch.shuffled()
                _isLoading.value = false

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

        // Initialize audio manager for volume control
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager?.let {
            val maxVol = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val curVol = it.getStreamVolume(AudioManager.STREAM_MUSIC)
            currentVolume = curVol.toFloat() / maxVol.coerceAtLeast(1)
        }

        // Load playback history from SharedPreferences
        loadHistory(context)

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

        if (_videoList.value.isNotEmpty()) {
            playVideoAtIndex(0)
            startProgressUpdater()
        }
    }

    fun playVideoAtIndex(index: Int) {
        val videos = _videoList.value
        if (videos.isEmpty() || index !in videos.indices) return
        if (index == lastPlayedIndex) return

        // Save position of previous video
        saveCurrentPosition()

        lastPlayedIndex = index
        _currentIndex.value = index
        exoPlayer?.apply {
            // Build media items: previous (for quick swipe back) + current + next
            val mediaItems = mutableListOf<MediaItem>()
            val prevIndex = (index - 1 + videos.size) % videos.size
            val nextIndex = (index + 1) % videos.size

            // Add prev, current, next — ExoPlayer will buffer ahead
            if (prevIndex != index) {
                mediaItems.add(MediaItem.fromUri(videos[prevIndex].uri))
            }
            mediaItems.add(MediaItem.fromUri(videos[index].uri))
            if (nextIndex != index && nextIndex != prevIndex) {
                mediaItems.add(MediaItem.fromUri(videos[nextIndex].uri))
            }

            // The current video is at position 1 if prev exists, else 0
            val currentMediaItemIndex = if (prevIndex != index) 1 else 0
            setMediaItems(mediaItems, currentMediaItemIndex, 0L)
            prepare()
            playWhenReady = true

            // Restore last position for this video (if within last 90% of duration)
            val savedPos = playbackHistory[videos[index].id]
            if (savedPos != null && savedPos > 0) {
                val duration = videos[index].duration
                if (duration > 0 && savedPos < duration * 0.9) {
                    seekTo(savedPos)
                }
            }

            // Preserve current speed (don't override if long-pressing)
            if (!_isLongPressSpeed.value) {
                playbackParameters = PlaybackParameters(_playbackState.value.playbackSpeed)
            }
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
        // Update immediately so UI reflects the new position without 500ms delay
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

    fun startLongPressSpeed() {
        if (_isLongPressSpeed.value) return
        speedBeforeLongPress = _playbackState.value.playbackSpeed
        _isLongPressSpeed.value = true
        exoPlayer?.playbackParameters = PlaybackParameters(2.0f)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = 2.0f)
    }

    fun endLongPressSpeed() {
        if (!_isLongPressSpeed.value) return
        _isLongPressSpeed.value = false
        exoPlayer?.playbackParameters = PlaybackParameters(speedBeforeLongPress)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = speedBeforeLongPress)
    }

    /**
     * Reset long-press state (called when swiping to another page)
     */
    fun resetLongPress() {
        if (_isLongPressSpeed.value) {
            _isLongPressSpeed.value = false
            exoPlayer?.playbackParameters = PlaybackParameters(speedBeforeLongPress)
            _playbackState.value = _playbackState.value.copy(playbackSpeed = speedBeforeLongPress)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.5f, 3.0f)
        // If long-pressing, update the "before" speed so it restores to this
        if (_isLongPressSpeed.value) {
            speedBeforeLongPress = clampedSpeed
        }
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
            val duration = it.duration
            // duration can be -1 (unset) or C.TIME_UNSET before media is ready
            val safeDuration = if (duration > 0) duration else 0L
            _playbackState.value = PlaybackState(
                isPlaying = it.isPlaying,
                currentPosition = it.currentPosition.coerceAtLeast(0),
                totalDuration = safeDuration,
                playbackSpeed = it.playbackParameters.speed
            )
        }
    }

    private fun startProgressUpdater() {
        progressUpdateJob?.cancel()
        progressUpdateJob = viewModelScope.launch {
            while (true) {
                updatePlaybackState()
                delay(500)
            }
        }
    }

    fun setBrightness(value: Float) {
        currentBrightness = value.coerceIn(0f, 1f)
        // Apply to window - caller must pass window reference
        // We store it here for the UI to read and apply
    }

    fun setVolume(value: Float) {
        currentVolume = value.coerceIn(0f, 1f)
        audioManager?.let { am ->
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVol = (currentVolume * maxVol).toInt().coerceIn(0, maxVol)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
        }
    }

    fun applyBrightnessToWindow(window: android.view.Window?) {
        window?.let {
            val layoutParams = it.attributes
            layoutParams.screenBrightness = currentBrightness
            it.attributes = layoutParams
        }
    }

    private fun loadHistory(context: Context) {
        try {
            val prefs = context.getSharedPreferences("playback_history", Context.MODE_PRIVATE)
            val json = prefs.getString("history", null) ?: return
            val obj = JSONObject(json)
            obj.keys().forEach { key ->
                playbackHistory[key.toLong()] = obj.getLong(key)
            }
        } catch (_: Exception) {}
    }

    private fun saveHistory(context: Context?) {
        try {
            val ctx = context ?: return
            val prefs = ctx.getSharedPreferences("playback_history", Context.MODE_PRIVATE)
            val obj = JSONObject()
            playbackHistory.forEach { (id, pos) ->
                obj.put(id.toString(), pos)
            }
            prefs.edit().putString("history", obj.toString()).apply()
        } catch (_: Exception) {}
    }

    fun saveCurrentPosition() {
        val player = exoPlayer ?: return
        val videos = _videoList.value
        val idx = _currentIndex.value
        if (idx in videos.indices) {
            playbackHistory[videos[idx].id] = player.currentPosition
        }
    }

    override fun onCleared() {
        super.onCleared()
        saveCurrentPosition()
        progressUpdateJob?.cancel()
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
