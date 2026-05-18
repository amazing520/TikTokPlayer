# TikTokPlayer 项目交接文档

## 📌 项目概述

仿抖音本地视频播放器 Android APP，完全离线，读取手机相册视频，上下滑动切换，支持倍速播放和定时关闭。

**仓库地址：** https://github.com/amazing520/TikTokPlayer
**技术栈：** Kotlin + Jetpack Compose + Media3 ExoPlayer + VerticalPager
**本地路径：** `/root/.openclaw/workspace/TikTokPlayer/`

---

## ✅ 已完成

### 核心功能
- [x] 读取本地相册视频（MediaStore API，适配 Android 13+ READ_MEDIA_VIDEO 和旧版 READ_EXTERNAL_STORAGE）
- [x] 竖屏全屏沉浸式播放（FLAG_FULLSCREEN + 透明状态栏/导航栏）
- [x] 上下滑动切换视频（Compose VerticalPager）
- [x] 随机循环播放（列表 shuffled，播完自动循环）
- [x] 倍速播放（0.5x / 0.75x / 1.0x / 1.25x / 1.5x / 2.0x / 3.0x）
- [x] 定时关闭（5/10/15/30/45/60/90/120 分钟，倒计时结束自动暂停+关闭 Activity）
- [x] 横竖屏自由切换（requestedOrientation 动态切换）
- [x] 点击屏幕显示/隐藏控制栏
- [x] 控制栏 3 秒自动隐藏（播放中）
- [x] 进度条拖动跳转
- [x] 屏幕常亮（播放中 FLAG_KEEP_SCREEN_ON）
- [x] 生命周期管理（ON_PAUSE 自动暂停）

### 项目结构
```
TikTokPlayer/
├── build.gradle.kts                    # 根构建文件 (AGP 8.2.2, Kotlin 1.9.22)
├── settings.gradle.kts                 # 项目设置
├── gradle.properties                   # Gradle 配置
├── gradle/wrapper/gradle-wrapper.properties  # Gradle 8.5
├── gradlew                             # Gradle Wrapper 脚本
├── app/
│   ├── build.gradle.kts                # App 构建 (compileSdk 34, minSdk 24)
│   ├── proguard-rules.pro              # 混淆规则
│   └── src/main/
│       ├── AndroidManifest.xml         # 权限声明 + Activity 配置
│       ├── java/com/tiktokplayer/
│       │   ├── MainActivity.kt         # 入口：权限处理 + Compose 宿主
│       │   ├── data/
│       │   │   ├── VideoItem.kt        # 数据类：id, uri, displayName, duration, size
│       │   │   └── MediaStoreRepository.kt  # 本地视频扫描（过滤 <1秒）
│       │   ├── viewmodel/
│       │   │   └── VideoPlayerViewModel.kt  # 播放状态管理 + ExoPlayer 控制
│       │   ├── screens/
│       │   │   └── VideoPlayerScreen.kt     # 主界面：VerticalPager + PlayerView
│       │   ├── components/
│       │   │   └── PlayerControlsOverlay.kt # 控制面板：进度条/倍速/定时/横竖屏
│       │   └── ui/theme/
│       │       └── Color.kt            # 颜色定义
│       └── res/
│           ├── values/                 # strings.xml, colors.xml, themes.xml
│           ├── drawable/               # 启动图标矢量图
│           ├── mipmap-*/               # 各分辨率启动图标
│           └── xml/                    # network_security_config.xml
└── README.md
```

---

## 🔧 已修复的 BUG（第二轮）

| 问题 | 严重性 | 修复方式 |
|------|--------|----------|
| 多个 PlayerView 争抢同一个 ExoPlayer → 滑动黑屏 | 🔴 Critical | 仅当前页挂载 player，非当前页 detach |
| Pager ↔ ViewModel 双向同步死循环 → 卡死 | 🔴 Critical | 改为单向驱动：滑动 → playVideoAtIndex，不再互相触发 |
| 点击屏幕无法显示/隐藏控制栏 | 🟡 Medium | 加 clickable modifier + toggleControls |
| 控制栏不自动隐藏 | 🟡 Medium | LaunchedEffect 监听 3 秒后自动 hide |
| shuffledIndices 冗余维护 | 🟢 Low | 直接对列表 shuffled()，更简洁 |

---

## ⚠️ 待解决问题 / 优化方向

### 高优先级
1. **CI 自动构建未配置** — `.github/workflows/build.yml` 未能推送到 GitHub（服务器无法直连 github.com，API 对 workflows 目录有限制）。需要在浏览器手动创建，内容见下方。
2. **Gradle Wrapper JAR 缺失** — `gradle/wrapper/gradle-wrapper.jar` 是二进制文件，未推送到 GitHub。CI 中用 `gradle wrapper --gradle-version 8.5` 自动生成，但本地 Android Studio 需要先 sync。
3. **ProGuard 规则不完整** — release 构建可能因混淆导致 ExoPlayer 崩溃，需要补充 keep 规则。

### 中优先级
4. **双击快进/快退** — 抖音标配功能，当前缺失。左半屏双击 -10s，右半屏双击 +10s。
5. **长按倍速** — 抖音长按 3x 播放，松手恢复。当前只有菜单选择。
6. **上下滑动手势冲突** — VerticalPager 内部的 clickable 和滑动手势可能冲突，需要更精细的手势处理（pointerInput + detectTapGestures）。
7. **视频预加载** — 当前 ExoPlayer 只加载当前视频，可以预加载相邻视频减少切换延迟。
8. **内存优化** — 大量视频时 MediaStoreRepository.getAllVideos() 一次性加载所有视频信息，应考虑分页。

### 低优先级
9. **视频封面/缩略图** — 滑动时显示视频第一帧作为过渡，而不是黑屏。
10. **播放历史记录** — 记录上次播放位置。
11. **手势亮度/音量调节** — 左侧上下滑动调亮度，右侧调音量。
12. **视频信息显示** — 文件名、时长、大小等。

---

## 📋 CI 工作流文件（需手动创建）

在 GitHub 仓库中创建 `.github/workflows/build.yml`，内容：

```yaml
name: Build APK

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3

      - name: Build Debug APK
        run: |
          gradle wrapper --gradle-version 8.5
          chmod +x gradlew
          ./gradlew assembleDebug --no-daemon

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: TikTokPlayer-debug
          path: app/build/outputs/apk/debug/app-debug.apk
          retention-days: 30
```

---

## 🔐 GitHub 认证

- 需要有效的 GitHub Personal Access Token（repo 权限）
- **API 可用**，`github.com` 直连不通（服务器网络限制）
- 推送方式：通过 GitHub Git Data API（创建 blob → tree → commit → update ref）
- **Contents API 对 `.github/workflows/` 目录无效**（404），需要用 Git Data API 的 tree 方式

---

## 🚀 新智能体接手步骤

1. **读取项目文件：** `read /root/.openclaw/workspace/TikTokPlayer/` 下的所有 .kt 文件
2. **推送到 GitHub：** 使用 GitHub API（token 如上），注意 `github.com` 不通，只能用 `api.github.com`
3. **创建 CI 文件：** 用 Git Data API 创建 tree → commit → update ref，不能用 Contents API
4. **继续开发：** 按上方"待解决"列表逐项优化
5. **构建测试：** 需要 Android SDK 环境，当前服务器没有，需要在有 Android Studio 的机器上构建

---

## 📊 依赖版本

| 依赖 | 版本 |
|------|------|
| Kotlin | 1.9.22 |
| AGP | 8.2.2 |
| Compose BOM | 2024.01.00 |
| Compose Compiler | 1.5.8 |
| Media3 ExoPlayer | 1.2.1 |
| Activity Compose | 1.8.2 |
| Lifecycle | 2.7.0 |
| compileSdk | 34 |
| minSdk | 24 |
| targetSdk | 34 |
| Gradle | 8.5 |
| JDK | 17 |
