# TikTokPlayer 项目交接文档（完整版）

## 📌 项目概述

仿抖音本地视频播放器 Android APP，完全离线运行，读取手机相册视频，上下滑动切换，支持倍速播放和定时关闭。

- **仓库地址：** https://github.com/amazing520/TikTokPlayer
- **本地路径：** `/root/.openclaw/workspace/TikTokPlayer/`
- **技术栈：** Kotlin + Jetpack Compose + Media3 ExoPlayer + VerticalPager
- **APK 下载：** GitHub Actions → 最新成功 Run → Artifacts → `TikTokPlayer-debug`
  - 直链（需登录 GitHub）：https://github.com/amazing520/TikTokPlayer/actions/runs/26040399392

---

## 🏗️ 完整项目结构

```
TikTokPlayer/
├── build.gradle.kts                         # 根构建文件 (AGP 8.2.2, Kotlin 1.9.22)
├── settings.gradle.kts                      # 项目设置
├── gradle.properties                        # Gradle 配置
├── gradle/wrapper/gradle-wrapper.properties # Gradle 8.5
├── gradlew                                  # Gradle Wrapper 脚本
├── build.yml                                # CI 工作流模板（根目录，需复制到 .github/workflows/）
├── HANDOFF.md                               # 本交接文档
├── README.md
├── app/
│   ├── build.gradle.kts                     # App 构建 (compileSdk 34, minSdk 24)
│   ├── proguard-rules.pro                   # 混淆规则（已完善）
│   └── src/main/
│       ├── AndroidManifest.xml              # 权限声明 + Activity 配置
│       ├── java/com/tiktokplayer/
│       │   ├── MainActivity.kt              # 入口：权限处理 + Compose 宿主
│       │   ├── data/
│       │   │   ├── VideoItem.kt             # 数据类
│       │   │   └── MediaStoreRepository.kt  # 本地视频扫描
│       │   ├── viewmodel/
│       │   │   └── VideoPlayerViewModel.kt  # 播放状态管理
│       │   ├── screens/
│       │   │   └── VideoPlayerScreen.kt     # 主界面
│       │   ├── components/
│       │   │   └── PlayerControlsOverlay.kt # 控制面板
│       │   └── ui/theme/
│       │       └── Color.kt                 # 颜色定义
│       └── res/
│           ├── layout/                      # exo_player_view.xml (TextureView 布局)
│           ├── values/                      # strings.xml, colors.xml, themes.xml
│           ├── drawable/                    # 启动图标矢量图
│           ├── mipmap-*/                    # 各分辨率启动图标（含 PNG fallback）
│           └── xml/                         # network_security_config.xml
```

---

## ✅ 已完成的全部功能（含各轮次修复）

### 核心功能
| # | 功能 | 状态 | 备注 |
|---|------|------|------|
| 1 | 读取本地相册视频 | ✅ | MediaStore API，适配 Android 13+ (READ_MEDIA_VIDEO) 和旧版 (READ_EXTERNAL_STORAGE) |
| 2 | 竖屏全屏沉浸式播放 | ✅ | FLAG_FULLSCREEN + 透明状态栏/导航栏 |
| 3 | 上下滑动切换视频 | ✅ | Compose VerticalPager |
| 4 | 随机循环播放 | ✅ | 列表 shuffled，播完自动循环 |
| 5 | 倍速播放 | ✅ | 0.5x / 0.75x / 1.0x / 1.25x / 1.5x / 2.0x / 3.0x |
| 6 | 定时关闭 | ✅ | 5/10/15/30/45/60/90/120 分钟，倒计时结束自动暂停+关闭 Activity |
| 7 | 横竖屏自由切换 | ✅ | requestedOrientation 动态切换 |
| 8 | 点击屏幕显示/隐藏控制栏 | ✅ | |
| 9 | 控制栏 3 秒自动隐藏 | ✅ | 播放中自动隐藏 |
| 10 | 进度条拖动跳转 | ✅ | |
| 11 | 屏幕常亮 | ✅ | 播放中 FLAG_KEEP_SCREEN_ON |
| 12 | 生命周期管理 | ✅ | ON_PAUSE 自动暂停 |

### 第三轮新增功能（智能体 #3）
| # | 功能 | 状态 | 备注 |
|---|------|------|------|
| 13 | 双击快进/快退 | ✅ | 左半屏 -10s，右半屏 +10s，带 "+10s"/"-10s" 反馈动画 |
| 14 | 长按倍速 | ✅ | 长按 2x 播放，顶部显示 "2x" 标识，松手恢复原速 |
| 15 | 视频预加载 | ✅ | 预加载前一个+当前+下一个视频（3个 MediaItem），减少切换延迟 |
| 16 | 分页加载 | ✅ | 首批 50 个视频快速加载，剩余后台加载 |
| 17 | ProGuard 规则完善 | ✅ | 覆盖 Media3、Compose、Kotlin Coroutines |

### 第四轮修复（智能体 #3 二次审查）
| # | 问题 | 严重性 | 修复方式 |
|---|------|--------|----------|
| 18 | 进度条拖动时闪烁回原位 | 🟡 | seekTo 后立即 updatePlaybackState()，不等 500ms 轮询 |
| 19 | 长按倍速 UI 暴露在非当前页 | 🟡 | 改为仅 isCurrentPage 时响应手势 |
| 20 | 控制栏点击区域穿透 | 🟡 | 添加 consume() 阻止事件穿透 |
| 21 | 时间格式不一致 | 🟢 | 统一 H:MM:SS / M:SS 格式 |
| 22 | 长按倍速恢复不正确 | 🟡 | 记住长按前的速度，松手后恢复 |
| 23 | mipmap 图标缺失 | 🔴 | adaptive-icon 移到 anydpi-v26，添加 PNG fallback |

### 第五轮新增功能（智能体 #4）
| # | 功能 | 状态 | 备注 |
|---|------|------|------|
| 24 | 视频缩略图 | ✅ | VideoItem 增加 thumbnailUri，通过 MediaStore.Video.Thumbnails 获取 |
| 25 | 播放历史记录 | ✅ | SharedPreferences 存储每个视频上次播放位置，自动恢复（<90% 时） |
| 26 | 手势亮度调节 | ✅ | 左侧上下滑动调节屏幕亮度（WindowManager.LayoutParams.screenBrightness） |
| 27 | 手势音量调节 | ✅ | 右侧上下滑动调节系统音量（AudioManager） |
| 28 | 视频信息显示 | ✅ | 底部左下角显示文件名、大小（MB/GB）、时长 |
| 29 | CI 自动构建 | ✅ | .github/workflows/build.yml 已推送，push 到 main 自动构建 |
| 30 | 编译错误修复 | ✅ | LocalLifecycleOwner import 路径、detectVerticalDragGestures 参数名 |

### 第六轮修复（智能体 #5 — 闪退 & 黑屏兼容性修复）
| # | 问题 | 严重性 | 修复方式 |
|---|------|--------|----------|
| 31 | 闪退：initializePlayer 无 try-catch | 🔴 | 整个方法包裹 try-catch，失败时显示错误提示而非崩溃 |
| 32 | 闪退：ExoPlayer 无 onPlayerError 监听 | 🔴 | 添加 Player.Listener.onPlayerError 回调，显示错误信息并 3 秒后自动清除 |
| 33 | 黑屏：PlayerView 缺少兼容性配置 | 🔴 | 添加 setKeepContentOnPlayerReset(true)，切换视频时保留最后一帧 |
| 34 | 闪退：saveHistory() 从未调用 | 🟡 | onCleared() 中调用 saveHistory(appContext)，播放历史现在正确持久化 |
| 35 | 缩略图：Android 10+ API 废弃 | 🟡 | 改用 contentResolver.loadThumbnail()，旧版回退到 Video.Thumbnails |
| 36 | 亮度：LaunchedEffect key 不响应变化 | 🟡 | 改用 snapshotFlow { viewModel.currentBrightness } 正确监听变化 |
| 37 | CI 编译错误 | 🔴 | 移除 setUseTextureView（旧 ExoPlayer API，Media3 不存在），回退 LocalLifecycleOwner 路径 |

### 第七轮优化（智能体 #6 — 黑屏根治 + 兼容性提升）
| # | 问题 | 严重性 | 修复方式 |
|---|------|--------|----------|
| 38 | 黑屏根治：SurfaceView 在国产机型兼容性差 | 🔴 | 新建 `res/layout/exo_player_view.xml`，设置 `app:surface_type="texture_view"`，代码改为 inflate XML 布局 |
| 39 | 缩略图同步加载卡主线程 | 🟡 | 引入 Coil 2.5.0 异步加载，MediaStoreRepository 不再同步生成 Bitmap |
| 40 | 手势冲突：亮度/音量拖拽与翻页冲突 | 🟡 | 自定义 `detectVerticalDragGesturesWithDirection`，先检测移动方向（10px 阈值），垂直主导才激活调节，水平主导让给 VerticalPager |
| 41 | 播放历史无限膨胀 | 🟢 | 新增 `pruneHistory()` — 加载视频后自动清理已删除视频的历史条目；ViewModel onCleared 时清理缩略图缓存 |
| 42 | 缩略图缓存无清理 | 🟢 | `onCleared()` 中调用 `cleanupThumbnailCache()`，删除 1 小时前的临时文件 |

---

## 🐛 已修复的全部 BUG

| 轮次 | 问题 | 严重性 | 修复方式 |
|------|------|--------|----------|
| 2 | 多个 PlayerView 争抢同一个 ExoPlayer → 滑动黑屏 | 🔴 | 仅当前页挂载 player，非当前页 detach |
| 2 | Pager ↔ ViewModel 双向同步死循环 → 卡死 | 🔴 | 改为单向驱动：滑动 → playVideoAtIndex |
| 2 | 点击屏幕无法显示/隐藏控制栏 | 🟡 | 加 clickable modifier + toggleControls |
| 2 | 控制栏不自动隐藏 | 🟡 | LaunchedEffect 监听 3 秒后自动 hide |
| 2 | shuffledIndices 冗余维护 | 🟢 | 直接对列表 shuffled() |
| 3 | 进度条拖动时闪烁回原位 | 🟡 | seekTo 后立即 updatePlaybackState() |
| 3 | 长按倍速 UI 暴露在非当前页 | 🟡 | 仅 isCurrentPage 时响应手势 |
| 3 | 控制栏点击区域穿透 | 🟡 | consume() 阻止事件穿透 |
| 3 | 时间格式不一致 | 🟢 | 统一 H:MM:SS / M:SS |
| 3 | 长按倍速恢复不正确 | 🟡 | 记住 speedBeforeLongPress |
| 3 | mipmap 图标缺失 | 🔴 | PNG fallback + anydpi-v26 |
| 4 | LocalLifecycleOwner import 路径错误 | 🔴 | 改为 `androidx.compose.ui.platform.LocalLifecycleOwner` |
| 4 | detectVerticalDragGestures 参数名错误 | 🔴 | `onDrag` → `onVerticalDrag` |
| 4 | dragAmount 类型推断失败 | 🟡 | 显式声明 `val delta: Float` |
| 5 | initializePlayer 无异常保护 | 🔴 | 添加 try-catch，失败显示错误提示 |
| 5 | ExoPlayer 无播放错误监听 | 🔴 | 添加 onPlayerError 回调 |
| 5 | PlayerView 缺少 keepContent 配置 | 🔴 | 添加 setKeepContentOnPlayerReset(true) |
| 5 | saveHistory 从未被调用 | 🟡 | onCleared 中调用 saveHistory |
| 5 | 缩略图 API 在 Android 10+ 废弃 | 🟡 | 改用 loadThumbnail API |
| 5 | 亮度 LaunchedEffect 不响应变化 | 🟡 | 改用 snapshotFlow |
| 5 | setUseTextureView 不存在于 Media3 | 🔴 | 移除，使用默认 PlayerView 配置 |
| 6 | SurfaceView 在国产设备黑屏 | 🔴 | XML 布局 `surface_type="texture_view"` + inflate 方式 |
| 6 | 缩略图同步加载大视频卡顿 | 🟡 | 引入 Coil 异步加载，移除同步 Bitmap 生成 |
| 6 | 亮度/音量手势与翻页冲突 | 🟡 | 方向感知拖拽检测（10px 阈值，垂直优先） |
| 6 | 播放历史无限膨胀 | 🟢 | pruneHistory 清理已删除视频条目 |

---

## ⚠️ 待解决 / 继续优化方向

### 已知问题
1. ~~**黑屏兼容性**~~ — ✅ 已修复：XML 布局 `surface_type="texture_view"` 替代默认 SurfaceView
2. ~~**缩略图缓存**~~ — ✅ 已修复：`onCleared()` 自动清理 + Coil 异步加载不再生成临时 Bitmap

### 中优先级
3. ~~**手势冲突优化**~~ — ✅ 已修复：方向感知拖拽检测
4. ~~**缩略图异步加载**~~ — ✅ 已修复：引入 Coil 2.5.0
5. **播放历史清理** — ✅ 已修复：pruneHistory 清理已删除视频条目。可进一步优化：记录最后播放时间戳，自动清理 30 天未播放的条目

### 低优先级
4. **视频删除功能** — 长按菜单删除不需要的视频
5. **更多手势** — 双击点赞动画、三击收藏等
6. **视频分类/文件夹筛选** — 按目录分类浏览
7. **深色/浅色主题切换**
8. **视频裁剪/片段循环**

---

## 🔐 GitHub 推送方式（重要！）

**`github.com` 直连不通！** 服务器网络限制，只能通过 `api.github.com` 推送。

### 推送流程（Git Data API）
```bash
# 1. 获取当前 main 的 SHA
curl -s -H "Authorization: token $TOKEN" "https://api.github.com/repos/amazing520/TikTokPlayer/git/refs/heads/main"

# 2. 创建 blob（base64 编码文件内容）
curl -s -X POST -H "Authorization: token $TOKEN" -H "Content-Type: application/json" \
    "https://api.github.com/repos/amazing520/TikTokPlayer/git/blobs" \
    -d '{"encoding":"base64","content":"<base64_content>"}'

# 3. 创建 tree（基于 parent tree，替换修改的文件）
curl -s -X POST -H "Authorization: token $TOKEN" -H "Content-Type: application/json" \
    "https://api.github.com/repos/amazing520/TikTokPlayer/git/trees" \
    -d '{"base_tree":"<parent_tree_sha>","tree":[{"path":"...","mode":"100644","type":"blob","sha":"<blob_sha>"}]}'

# 4. 创建 commit
curl -s -X POST -H "Authorization: token $TOKEN" -H "Content-Type: application/json" \
    "https://api.github.com/repos/amazing520/TikTokPlayer/git/commits" \
    -d '{"message":"...","tree":"<tree_sha>","parents":["<parent_sha>"]}'

# 5. 更新 ref
curl -s -X PATCH -H "Authorization: token $TOKEN" -H "Content-Type: application/json" \
    "https://api.github.com/repos/amazing520/TikTokPlayer/git/refs/heads/main" \
    -d '{"sha":"<commit_sha>"}'
```

### ⚠️ CI 文件推送注意
- **Contents API 对 `.github/workflows/` 目录返回 404**
- **必须用 Git Data API 的 tree 方式推送**
- 推送脚本参考：`/tmp/push_workflow.sh`

---

## 📋 CI 工作流

文件位置：`.github/workflows/build.yml`（已推送到 GitHub）

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

### 下载 APK
1. 打开 https://github.com/amazing520/TikTokPlayer/actions
2. 点击最新的绿色 ✅ 成功 Run
3. 下载 Artifacts → `TikTokPlayer-debug`（约 18MB zip）

---

## 📊 依赖版本

| 依赖 | 版本 |
|------|------|
| Kotlin | 1.9.22 |
| AGP | 8.2.2 |
| Compose BOM | 2024.01.00 |
| Compose Compiler | 1.5.8 |
| Media3 ExoPlayer | 1.2.1 |
| Coil Compose | 2.5.0 |
| Activity Compose | 1.8.2 |
| Lifecycle | 2.7.0 |
| compileSdk | 34 |
| minSdk | 24 |
| targetSdk | 34 |
| Gradle | 8.5 |
| JDK | 17 |

---

## 🚀 新智能体接手步骤

1. **读取全部源码：** `read /root/.openclaw/workspace/TikTokPlayer/` 下所有 `.kt` 文件
2. **读取本交接文档：** `HANDOFF.md`
3. **理解推送方式：** github.com 不通，必须用 api.github.com 的 Git Data API（见上方）
4. **继续优化：** 按"待解决"列表逐项推进
5. **构建 APK：** 推送到 GitHub 后 CI 自动构建，从 Actions 下载
6. **更新 HANDOFF.md：** 每次提交后同步更新交接文档

---

## 📝 Git 提交历史

```
0618bf3 fix: 修复编译错误 - LocalLifecycleOwner 路径回退、移除 setUseTextureView
a27d2ce fix: 修复闪退和黑屏兼容性问题 - try-catch/error listener/keepContent/snapshotFlow
3d00075 fix: resolve compilation errors - LocalLifecycleOwner import, drag gesture parameter types
be05581 feat: add video thumbnails, playback history, brightness/volume gestures, improved preloading
449ae3c fix: mipmap icons - move adaptive-icon to anydpi-v26, add PNG fallbacks
44d8378 ci: add GitHub Actions build workflow for APK
dbdb49c review: 二次审查修复 - seekTo延迟/死代码清理/未用import
4efc44a fix: 全面审查修复 - 进度条闪烁/手势冲突/时间格式/长按状态
f93a934 fix: 长按倍速改为2倍速
358da40 feat: 双击快进、长按倍速、视频预加载、分页加载、ProGuard完善
483ae75 docs: add project handoff document
e1b8300 fix: 修复滑动黑屏、同步冲突、控制栏交互
```

---

## ⚡ 关键技术决策记录

1. **为什么用 Git Data API 而不是 Contents API？** → `.github/workflows/` 目录在 Contents API 下返回 404，GitHub 对此目录有特殊限制
2. **为什么 LocalLifecycleOwner 用 `androidx.compose.ui.platform` 而不是 `androidx.lifecycle.compose`？** → Compose BOM 2024.01.00 对应的版本中，后者未被识别，使用前者兼容性更好
3. **为什么预加载用 3 个 MediaItem 而不是单独的 ExoPlayer？** → ExoPlayer 原生支持播放列表预加载，无需管理多个 Player 实例，更省内存
4. **为什么播放历史用 SharedPreferences 而不是 Room？** → 数据量小（仅 videoId→position 映射），SharedPreferences 更简单，无需额外依赖
5. **为什么不能用 setUseTextureView？** → 这是旧版 ExoPlayer 2 的 API，Media3 1.2.1 的 PlayerView 没有此方法。Media3 中 SurfaceView 是默认行为，TextureView 需要通过 XML `app:surface_type="texture_view"` 或 `setVideoTextureView()` 设置
6. **为什么 LocalLifecycleOwner 用 `androidx.compose.ui.platform`？** → `lifecycle-runtime-compose:2.7.0` 还没有把 `LocalLifecycleOwner` 搬到 `androidx.lifecycle.compose`（需要 2.8.0+），用旧路径兼容
7. **为什么黑屏用 XML inflate 而不是代码创建 TextureView？** → XML 的 `app:surface_type="texture_view"` 是 Media3 官方推荐方式，确保 PlayerView 内部正确初始化 TextureView 的生命周期和渲染管线。代码调用 `setVideoTextureView()` 可能遗漏内部初始化步骤
8. **为什么缩略图改用 Coil 而不是继续用 contentResolver.loadThumbnail？** → 同步加载大视频缩略图会阻塞主线程造成卡顿，Coil 自带内存/磁盘缓存、自动取消、协程异步加载，且原生支持从视频 URI 提取帧
9. **手势方向检测阈值为什么是 10px？** → 太小容易误判（手指微抖就触发），太大会让调节响应迟钝。10px 在大多数设备上约 2-3mm，用户能明确表达意图
