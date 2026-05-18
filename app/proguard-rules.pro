# === TikTokPlayer ProGuard Rules ===

# Keep application entry points
-keep class com.tiktokplayer.** { *; }

# === Media3 / ExoPlayer ===
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ExoPlayer extension classes (loaded via reflection)
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# Media3 session
-keep class androidx.media3.session.** { *; }

# Keep metadata for MediaItem
-keepclassmembers class androidx.media3.common.MediaItem** { *; }

# === Kotlin Coroutines ===
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# === Jetpack Compose ===
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# === Android Core ===
-keep class androidx.core.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.activity.** { *; }

# === Data classes ===
-keepclassmembers class com.tiktokplayer.data.** { *; }
-keepclassmembers class com.tiktokplayer.viewmodel.PlaybackState { *; }
-keepclassmembers class com.tiktokplayer.viewmodel.TimerState { *; }
