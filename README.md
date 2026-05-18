# TikTokPlayer - 仿抖音本地视频播放器

## 功能特性

- 📱 **竖屏全屏沉浸式播放** - 默认竖屏全屏，支持切换横屏
- 👆 **上下滑动切换** - 仿抖音滑动交互逻辑
- 🎬 **本地视频读取** - 仅读取手机相册视频，完全离线
- 🔄 **随机循环播放** - 所有视频随机顺序，循环播放
- ⚡ **倍速播放** - 支持 0.5x ~ 3.0x 倍速调节
- ⏰ **定时关闭** - 设置时长，倒计时结束自动停止播放并关闭APP
- 🎯 **手势控制** - 点击暂停/播放，拖动进度条

## 构建方式

### 前置要求
- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 34

### 构建步骤

1. 用 Android Studio 打开项目目录
2. 等待 Gradle 同步完成
3. 连接手机或启动模拟器
4. 点击 Run 运行

### 命令行构建

```bash
# 确保 ANDROID_HOME 环境变量已设置
export ANDROID_HOME=/path/to/android/sdk

# 构建 debug APK
./gradlew assembleDebug

# APK 输出位置
# app/build/outputs/apk/debug/app-debug.apk
```

## 项目结构

```
app/src/main/java/com/tiktokplayer/
├── MainActivity.kt          # 主Activity，权限处理
├── data/
│   ├── VideoItem.kt         # 视频数据模型
│   └── MediaStoreRepository.kt  # 本地视频扫描
├── viewmodel/
│   └── VideoPlayerViewModel.kt  # 播放状态管理
├── components/
│   └── PlayerControlsOverlay.kt  # 播放控制UI
├── screens/
│   └── VideoPlayerScreen.kt  # 视频播放主界面
└── ui/theme/
    └── Color.kt              # 主题颜色定义
```

## 技术栈

- **Kotlin** - 开发语言
- **Jetpack Compose** - 声明式UI
- **Media3 ExoPlayer** - 视频播放引擎
- **VerticalPager** - 竖向滑动切换
- **MediaStore** - 本地视频扫描

## 权限说明

- `READ_MEDIA_VIDEO` (Android 13+) - 读取视频文件
- `READ_EXTERNAL_STORAGE` (Android 12及以下) - 读取外部存储

## 使用说明

1. 首次启动需要授予存储权限
2. 上下滑动切换视频
3. 点击屏幕显示/隐藏控制面板
4. 底部进度条可拖动跳转
5. 点击倍速按钮调节播放速度
6. 点击时钟图标设置定时关闭
7. 右上角按钮切换横竖屏
