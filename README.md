# Folder Player

一个专为本地及云端音频收藏设计的极简、美观且功能强大的 Android 文件夹音乐播放器。

---

## 核心特性

- **📂 深度文件夹浏览**：直接按目录播放音乐，无需复杂的媒体库扫描，支持本地存储与 WebDAV (Alist/NAS)。
- **📑 CUE 整轨支持**：完美解析 `.cue` 文件，支持 FLAC/WAV/APE 的虚拟分轨播放，即使 App 重启也能精准恢复分轨状态。
- **📜 全方位歌词体验**：支持本地 `.lrc` 文件、音频内嵌歌词（ID3 Tags）以及自定义歌词 API 接口。
- **🚀 零感知恢复**：App 重启后瞬间显示最后播放的歌曲、封面及歌词，完全消除“未在播放”的闪烁感。
- **🎨 现代极致设计**：全黑深色主题，支持封面显示大小切换，界面精致、动画流畅。
- **🎧 智能交互**：
    - 耳机断开自动暂停。
    - 播放列表紧凑排列，序号与标题完美对齐。
    - 支持源管理及拖拽/移动排序。
    - 支持文件/文件夹修改日期显示。
    - 自动下一文件夹播放。

## 技术栈

- **Jetpack Compose**：全响应式现代 UI 框架。
- **Media3 (ExoPlayer)**：高性能音频流与分轨播放引擎。
- **Coroutines & Flow**：响应式状态管理。
- **Sardine-Android**：稳定可靠的 WebDAV 通讯库。
- **Coil**：高效封面图像缓存与加载。

---

# Folder Player (English)

A minimalist, beautiful, and powerful Android music player designed specifically for local and cloud folder-based audio collections.

## Key Features

- **📂 Deep Folder Browsing**: Play music directly by directory without complex library scanning. Supports Local Storage and WebDAV (Alist/NAS).
- **📑 CUE Sheet Support**: Perfectly parses `.cue` files, supporting virtual track splitting for FLAC/WAV/APE. Restores split-track state accurately even after app restart.
- **📜 Comprehensive Lyrics**: Supports local `.lrc` files, embedded ID3 tag lyrics, and custom online Lyric API integration.
- **🚀 Seamless Resume**: Instantly restores the last played song, cover art, and lyrics upon app startup, eliminating the "No Song" flicker entirely.
- **🎨 Premium Design**: Pure black dark theme with configurable cover sizes, featuring a refined interface and smooth animations.
- **🎧 Intelligent Interaction**:
    - Auto-pause on headphone disconnection.
    - Compact playlist UI with pixel-perfect track number alignment.
    - Source management with reordering support.
    - File/Folder modification timestamps in the browser.
    - Auto-next folder playback.

## Tech Stack

- **Jetpack Compose**: Modern declarative UI framework.
- **Media3 (ExoPlayer)**: High-performance engine for audio streaming and clipping.
- **Coroutines & Flow**: Reactive state management.
- **Sardine-Android**: Robust WebDAV communication library.
- **Coil**: Efficient cover art caching and loading.
