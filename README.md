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

## 操作说明

### 1. 基础导航
- App 采用三屏滑动设计：**[播放页] <-> [文件浏览器] <-> [设置]**。
- 您可以左右滑动屏幕，或点击右上角的图标快速切换。

### 2. 文件播放
- **单曲播放**：在浏览器中点击任意音频文件即可开始播放当前文件夹。
- **整轨/CUE 播放**：点击 `.cue` 文件，App 会自动解析并显示分轨列表。
- **文件夹播放**：点击文件夹右侧的“播放”图标，或在文件夹内点击顶部的“播放当前文件夹”。
- **随机播放**：在文件夹内点击顶部的“随机播放”图标，将随机打乱当前目录下的所有歌曲。

### 3. 源管理 (WebDAV/NAS)
- 在浏览器主界面（Root），点击 **"Add WebDAV"**。
- 输入您的服务器地址、路径、用户名及密码。
- **排序/删除**：点击源右侧的展开图标，可以进行 **"Move Up/Down"** 排序或 **"Delete"** 删除。

### 4. 播放列表管理
- 在播放页点击右上角的“列表”图标呼出底栏（BottomSheet）。
- 播放列表支持紧凑显示，可快速跳转曲目。

### 5. 设置项
- **封面大小**：支持切换 Standard（标准）与 Large（大封面）模式。
- **歌词 API**：可手动配置在线歌词接口地址。
- **自动下一文件夹**：当开启后，当前目录播放完毕会自动开始下一个目录。

---

# Folder Player (English)

A minimalist, beautiful, and powerful Android music player designed specifically for local and cloud folder-based audio collections.

## Key Features

- **📂 Deep Folder Browsing**: Play music directly by directory without complex library scanning. Supports Local Storage and WebDAV (Alist/NAS).
- **📑 CUE Sheet Support**: Perfectly parses `.cue` files, supporting virtual track splitting for FLAC/WAV/APE. Restores split-track state accurately even after app restart.
- **📜 Comprehensive Lyrics**: Supports local `.lrc` files, embedded ID3 tag lyrics, and custom online Lyric API integration.
- **🚀 Seamless Resume**: Instantly restores the last played song, cover art, and lyrics upon app startup, eliminating the "No Song" flicker entirely.
- **🎨 Premium Design**: Pure black dark theme with configurable cover sizes, featuring a refined interface and smooth animations.

## Usage Instructions

### 1. Basic Navigation
- The app uses a three-screen pager: **[Player] <-> [Browser] <-> [Settings]**.
- Swipe left/right or tap the icons in the header to navigate.

### 2. Playback
- **Single Track**: Tap any audio file in the browser to start playing the folder from that song.
- **CUE Sheets**: Tap a `.cue` file to parse and play split tracks automatically.
- **Folder Playback**: Tap the "Play" icon next to a folder name or use the "Play Current" button inside a folder.
- **Shuffle**: Use the "Shuffle" icon at the top of a folder to play all contents in random order.

### 3. Source Management (WebDAV/NAS)
- Tap **"Add WebDAV"** on the browser root screen.
- Enter your server URL, path, and credentials.
- **Manage Sources**: Expand a source item to access **"Move Up/Down"** for reordering or **"Delete"** to remove.

### 4. Playlist Management
- Tap the "Playlist" icon in the top right of the player screen to open the BottomSheet.
- The compact list allows for quick navigation and track selection.

### 5. Settings
- **Cover Size**: Toggle between Standard and Large cover display modes.
- **Lyric API**: Configure a custom URL for fetching online lyrics.
- **Auto-Next Folder**: Enable this to automatically play the next directory when the current one finishes.
