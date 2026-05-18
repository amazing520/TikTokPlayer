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
- [x] **双击快进/快退** — 左半屏 -10s，右半屏 +10s，带反馈动画
- [x] **长按倍速** — 长按 2x 播放，松手恢复原速
- [x] **视频预加载** — 预加载前一个+当前+下一个视频，减少切换延迟
- [x] **视频缩略图** — 滑动时显示视频封面，避免黑屏闪烁
- [x] **播放历史记录** — 记忆每个视频上次播放位置，自动恢复（<90%时）
- [x] **手势亮度调节** — 左侧上下滑动调节屏幕亮度
- [x] **手势音量调节** — 右侧上下滑动调节系统音量
- [x] **视频信息显示** — 底部显示文件名、大小、时长

### 项目结构
```
TikTokPlayer/
├── build.gradle.kts                    # 根构建文件 (AGP 8.2.2, Kotlin 1.9.22)
├── settings.gradle.kts                 # 项目设置
├── gradle.properties                   # Gradle 配置
├── gradle/wrapper/gradle-wrapper.properties  # Gradle 8.5
├── gradlew                             # Gradle Wrapper 脚本
├── build.yml                           # CI 工作流模板
├── app/
│   ├── build.gradle.kts                # App 构建 (compileSdk 34, minSdk 24)
│   ├── proguard-rules.pro              # 混淆规则
│   └── src/main/
│       ├── AndroidManifest.xml         # 权限声明 + Activity 配置
│       ├── java/com/tiktokplayer/
│       │   ├── MainActivity.kt         # 入口：权限处理 + Compose 宿主
│       │   ├── data/
│       │   │   ├── VideoItem.kt        # 数据类：id, uri, displayName, duration, size, thumbnailUri
│       │   │   └── MediaStoreRepository.kt  # 本地视频扫描（分页+缩略图）
│       │   ├── viewmodel/
│       │   │   └── VideoPlayerViewModel.kt  # 播放状态+手势+历史+亮度/音量
│       │   ├── screens/
│       │   │   └── VideoPlayerScreen.kt     # 主界面：VerticalPager+手势+缩略图+信息
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

## 🔧 已修复的 BUG

| 问题 | 严重性 | 修复方式 |
|------|--------|----------|
| 多个 PlayerView 争抢同一个 ExoPlayer → 滑动黑屏 | 🔴 Critical | 仅当前页挂载 player，非当前页 detach |
| Pager ↔ ViewModel 双向同步死循环 → 卡死 | 🔴 Critical | 改为单向驱动：滑动 → playVideoAtIndex，不再互相触发 |
| 点击屏幕无法显示/隐藏控制栏 | 🟡 Medium | 加 clickable modifier + toggleControls |
| 控制栏不自动隐藏 | 🟡 Medium | LaunchedEffect 监听 3 秒后自动 hide |
| shuffledIndices 冗余维护 | 🟢 Low | 直接对列表 shuffled()，更简洁 |

---

## ⚠️ 待解决 / 优化方向

### 低优先级
1. **手势冲突优化** — VerticalPager 内部的 clickable 和垂直滑动手势可能需要更精细的 pointerInput 处理
2. **缩略图异步加载** — 当前用 AndroidView + setImageURI 同步加载，大视频可能卡顿，可改用 Coil 等图片库
3. **播放历史清理** — 超过 30 天的历史记录自动清理
4. **视频删除功能** — 长按菜单删除不需要的视频

---

## 📋 CI 自动构建

✅ `.github/workflows/build.yml` 已配置完成并推送到 GitHub

- 推送到 `main` 分支自动触发构建
- 支持手动触发 (`workflow_dispatch`)
- 构建产物保留 30 天
- **下载 APK**: GitHub 仓库 → Actions → 选择最新 run → Artifacts → TikTokPlayer-debug

---

## 🔐 GitHub 认证

- Token 已配置，API 推送正常
- `github.com` 直连不通（服务器网络限制），通过 `api.github.com` 推送
- CI 文件使用 Git Data API（tree 方式）推送

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

---

## 🚀 新智能体接手步骤

1. **读取项目文件：** `read /root/.openclaw/workspace/TikTokPlayer/` 下的所有 .kt 文件
2. **推送到 GitHub：** 使用 GitHub API（token 如上），注意 `github.com` 不通，只能用 `api.github.com`
3. **创建 CI 文件：** 用 Git Data API 创建 tree → commit → update ref，不能用 Contents API
4. **继续开发：** 按上方"待解决"列表逐项优化
5. **构建测试：** 需要 Android SDK 环境，当前服务器没有，需要在有 Android Studio 的机器上构建
