# MP3 播放器（Android）

一个原生 Android MP3 播放器，支持扫描播放本地音乐，以及在线搜索和下载免费音乐（Jamendo Creative Commons 曲库）。

## 功能特性

- **本地音乐扫描**：自动扫描手机中的 MP3/音频文件，按标题排序显示
- **完整播放控制**：播放/暂停、上一首/下一首、进度拖动、随机播放、列表循环、单曲循环
- **后台播放**：前台服务 + 通知栏控制，锁屏后仍可继续播放
- **在线发现**：通过 Jamendo API 搜索数百万首免费正版音乐
- **在线下载**：将搜索到的免费音乐下载到应用私有目录，离线可播放
- **下拉刷新**：本地列表和在线搜索结果均支持下拉刷新
- **全屏播放器**：点击底部迷你播放器展开全屏控制界面

---

## 一、获取 APK（安装到手机）

### 方案 A：GitHub Actions 云端构建（推荐，电脑上无需安装任何开发工具）

1. 在 GitHub 上创建一个新仓库（例如 `MP3Player`）
2. 把本项目**所有文件上传**到该仓库（包括隐藏的 `.github` 文件夹）
3. 打开仓库页面，点击顶部的 **Actions** 标签
4. 在左侧选择 **Build Android APK**，点击右侧的 **Run workflow** → **Run workflow**
5. 等待构建完成（通常 2-5 分钟），在构建结果页面底部找到 **Artifacts** → **MP3Player-APK**
6. 下载并解压，得到 `app-debug.apk`
7. 把 APK 传到安卓手机，点击安装（需允许"安装未知来源应用"）

> 每次 `git push` 代码也会自动触发构建。

### 方案 B：Android Studio 本地构建

1. 安装 [Android Studio](https://developer.android.com/studio)（自带 JDK 和 Android SDK）
2. 打开 Android Studio，选择 **Open**，选中本项目的 `MP3Player` 文件夹
3. 等待 Gradle 同步完成（首次可能需要下载依赖，需要联网）
4. 手机开启"开发者选项"和"USB调试"，用数据线连接电脑
5. 点击工具栏的 **Run** 按钮（绿色三角形），应用会自动安装到手机上

> 如果想生成 APK 文件：菜单 **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)**，完成后在 `app/build/outputs/apk/debug/` 找到 `app-debug.apk`。

### 方案 C：命令行构建

需要本机已安装 **JDK 17**、**Gradle 8.4**、**Android SDK（API 34）**，并配置好 `ANDROID_HOME` 环境变量：

```bash
gradle assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

---

## 二、配置在线音乐搜索（Jamendo API Key）

在线搜索功能使用 [Jamendo API](https://developer.jamendo.com/)，需要免费的 `client_id`。获取步骤：

1. 访问 <https://developer.jamendo.com/> 注册一个免费账号
2. 登录后进入 **API Keys / My Apps** 页面，创建一个应用
3. 复制生成的 **Client ID**
4. 在 App 中点击右上角菜单 → **设置**，粘贴 Client ID 并保存
5. 或者在"在线发现" tab 顶部点击黄色提示文字，也可以打开设置对话框

配置完成后即可搜索和下载免费音乐。

> Jamendo 上的音乐采用 Creative Commons 协议，可免费下载使用，请遵守相应的署名要求。

---

## 三、使用说明

1. **首次打开**：App 会请求"文件和媒体"读取权限，请允许，否则无法扫描本地音乐
2. **本地音乐 tab**：显示扫描到的所有音频文件，点击任意歌曲开始播放
3. **在线发现 tab**：输入关键词（如 `jazz`、`rock`、`lofi`）搜索免费音乐，点击歌曲在线播放，点击下载按钮保存到本地
4. **播放控制**：底部迷你播放器显示当前歌曲，点击歌曲信息区域展开全屏播放器
5. **通知栏**：播放时通知栏显示控制按钮，可在锁屏和后台控制播放
6. **设置**：右上角菜单 → 设置，可修改 Jamendo API Key

---

## 四、项目结构

```
MP3Player/
├── .github/workflows/build-apk.yml   # GitHub Actions 自动构建配置
├── app/
│   ├── src/main/
│   │   ├── java/com/mp3player/app/
│   │   │   ├── MainActivity.kt              # 主界面
│   │   │   ├── MusicPlayerService.kt        # 后台播放服务
│   │   │   ├── MusicBroadcastReceiver.kt    # 通知栏控制接收器
│   │   │   ├── LocalMusicFragment.kt        # 本地音乐页面
│   │   │   ├── OnlineMusicFragment.kt       # 在线发现页面
│   │   │   ├── FullPlayerDialog.kt          # 全屏播放器
│   │   │   ├── adapter/                     # 列表适配器
│   │   │   └── data/                        # 数据模型与仓库
│   │   └── res/                             # 布局、图片、字符串资源
│   ├── build.gradle.kts
│   └── src/main/AndroidManifest.xml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## 五、常见问题

**Q：构建失败，提示 SDK 版本问题？**
A：确保使用 Android Studio 自带的 SDK，或设置 `ANDROID_HOME` 指向已安装 API 34 的 SDK 目录。

**Q：本地音乐列表为空？**
A：确认已授予存储权限，且手机中确实有 MP3 文件。可以在列表页下拉刷新重新扫描。

**Q：在线搜索没有结果？**
A：检查是否已配置 Jamendo API Key，以及网络连接是否正常。提示文字会引导你进入设置。

**Q：下载的歌曲在哪里？**
A：保存在应用私有目录 `Android/data/com.mp3player.app/files/Music/Downloads/`，仅本应用可访问。
