# Folder Player

一个专为本地及云端音频收藏设计的极简、美观且功能强大的 Android 文件夹音乐播放器。

---

## 核心特性

- **📂 深度文件夹浏览**：直接按目录播放音乐，无需复杂的媒体库扫描，支持本地存储与 WebDAV协议 (Alist/群晖/飞牛等）。
- **📑 CUE 整轨支持**：完美解析 `.cue` 文件，支持 FLAC/WAV 的虚拟分轨播放，即使 App 重启也能精准恢复分轨状态。
- **📜 全方位歌词体验**：支持本地 `.lrc` 文件、音频内嵌歌词（ID3 Tags）以及自定义歌词 API 接口。
- **🚀 零感知恢复**：App 重启后瞬间显示最后播放的歌曲、封面及歌词。
- **🎨 现代极致设计**：深色主题，支持封面显示大小切换，界面精致、动画流畅。
- **🎧 智能交互**：
    - 耳机断开自动暂停。
    - 支持源管理及复制/移动排序。
    - 支持文件夹手动排序的持久化记忆。
    - 支持文件/文件夹日期显示。

## 操作说明

### 1. 基础导航
- App 采用三屏滑动设计：**[播放页] <-> [文件浏览器] <-> [设置]**。
- 您可以左右滑动屏幕，或点击右上角的图标快速切换。

### 2. 文件播放
- **单曲/文件夹播放**：在浏览器中点击任意音频文件即可开始播放当前文件夹。
- **整轨/CUE 播放**：点击 `.cue` 文件，App 会自动解析并显示分轨列表。
- **随机播放歌曲**：在文件夹内点击顶部的“随机播放”图标，将随机打乱当前目录下的所有歌曲。
- **随机播放文件夹**：在下挂多个专辑文件夹的文件夹点击顶部的“随机播放”图标，将随机播放其中一个专辑文件夹。

### 3. 无限播放 (自动下一文件夹)
- 在播放页上划呼出播放列表，可看到 **“无限播放”** 按钮。
- **开启后**：当前文件夹播放完毕，App 会自动寻找并开始播放下一个目录，实现真正的“无限”畅听。

### 4. 智能排序
- **默认排序**：在 [设置] 页面中，您可以配置全局默认的排序方式（按名称、日期或大小）。
- **个性化记忆**：在任何文件夹内手动切换排序方式（点击标题栏的排序图标）后，App 会**自动记住该路径的专属排序方式**，下次进入时将自动应用。

### 5. 源管理 (WebDAV/NAS)
- 在文件浏览器主界面，点击 **"Add WebDAV"**。
- 输入您的服务器地址（注意Alist服务在末尾需加/dav）、路径（可选）、用户名及密码。
- 可复制源进行快速编辑，通过不同的路径指向不同的音乐分类，可代替收藏夹使用。
- **排序/删除**：点击源右侧的展开图标，可以进行 **"Move Up/Down"** 排序或 **"Delete"** 删除。

### 6. 设置项
- **封面大小**：支持切换 Standard（标准）与 Large（大封面）模式。
- **歌词 API**：可手动配置在线歌词接口地址，默认地址可直接使用。

歌词服务来自：https://github.com/HisAtri/LrcApi ，建议自行搭建，感谢作者提供了公开API。

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

### 3. Infinite Play (Auto-Next Folder)
- Open the playlist (top right icon on Player screen) to find the **"Infinite Play"** toggle.
- **When enabled**: The app will automatically jump to the next available directory once the current one finishes, providing an uninterrupted listening experience.

### 4. Intelligent Sorting
- **Default Sort**: Configure your preferred global sorting method (By Name, Date, or Size) in the [Settings] screen.
- **Path-Specific Memory**: If you manually change the sort order in a specific folder, the app will **automatically remember that setting for that specific path** and apply it every time you return.

### 5. Source Management (WebDAV/NAS)
- Tap **"Add WebDAV"** on the browser root screen.
- Enter your server URL, path, and credentials.
- **Manage Sources**: Expand a source item to access **"Move Up/Down"** for reordering or **"Delete"** to remove.

### 6. Settings
- **Cover Size**: Toggle between Standard and Large cover display modes.
- **Lyric API**: Configure a custom URL for fetching online lyrics.
