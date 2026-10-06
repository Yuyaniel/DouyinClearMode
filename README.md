# 抖音清爽模式 (Douyin Clear Mode)

LSPosed 模块：长按屏幕**左上角宫格**进入/退出「清爽模式」。进入后，视频播放页的叠加控件（顶栏/底栏/音乐转盘等）在**播放时隐藏**，**暂停时显示**。

- 目标包名：`com.ss.android.ugc.awemf`（注意是 `awemf`，不是 `aweme`）
- 目标版本：`25.3.0`
- LSPosed API：`102`
- 语言：Java
- 打包：GitHub Actions（本仓库 `.github/workflows/build.yml`）

## 功能

| 能力 | 说明 |
|---|---|
| 清爽模式 | 长按左上角宫格进入/退出 |
| 播放隐藏控件 | 播放中隐藏叠加控件 |
| 暂停显示控件 | 暂停时恢复显示 |
| 模块 UI | 保存配置 / 展示框架信息 / 热重载入口 |

## 工程结构

```
douyin-clear-mode/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/douyin/clearmode/
│       │   ├── App.java                 # service 注册
│       │   ├── ModuleEntry.java         # Hook 入口（手势 + 播放状态）
│       │   ├── ClearModeController.java # 控件显隐控制
│       │   ├── ClearModePrefs.java      # Remote Preferences 缓存
│       │   └── MainActivity.java        # 模块 UI
│       ├── res/...
│       └── resources/META-INF/xposed/
│           ├── module.prop
│           ├── scope.list
│           └── java_init.list
└── .github/workflows/build.yml
```

## 构建

### 方式一：GitHub Actions（推荐）

1. 将本工程推送到 GitHub 仓库。
2. 仓库根目录含 `.github/workflows/build.yml`，推送 / PR / 手动触发后自动构建。
3. 在 Actions 的 Artifacts 下载 `douyin-clear-mode` APK。

### 方式二：本地（需 JDK17 + Android SDK）

```bash
# 建议先在本地生成 wrapper（需要已安装 gradle）
gradle wrapper --gradle-version 8.7
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

## 安装与启用

1. 安装 APK 到 Android 设备（需 LSPosed / 支持 API 102 的框架）。
2. 打开 LSPosed 管理器，确认模块「抖音清爽模式」已启用。
3. 在模块配置页勾选作用域：`com.ss.android.ugc.awemf`。
4. 重启抖音。
5. 进入主页 feed 播放页，长按**左上角宫格**，测试进入/退出清爽模式。

## 使用的 Hook 锚点

> 以下锚点基于 APK 静态逆向取证（包名 `com.ss.android.ugc.awemf`）。

| 锚点 | 说明 | 状态 |
|---|---|---|
| `com.ss.android.ugc.aweme.main.MainActivity` | 主页宿主 Activity，`dispatchTouchEvent` 四宫格长按手势 | 已确认 |
| `com.ss.android.ugc.aweme.player.sdk.api.OnUIPlayListener` | 播放状态接口，`onPlaying`/`onPlayPause` 驱动控件显隐 | 已确认 |
| 叠加控件容器定位 | 采用「视图树遍历 + 特征匹配」启发式 | 待验证 |

### 验证与排错

- 查看日志：`LSPosed` 日志过滤 `ClearMode`。
- 期望日志序列：
  - `event=module_loaded` → 模块注入成功
  - `event=hook_gesture_registered` → 手势 Hook 生效
  - `event=hook_play_state_registered` → 播放状态 Hook 生效
  - `event=enter_clear` / `event=exit_clear` → 长按触发
  - `event=overlay_visibility ...` → 控件显隐执行
- 若 `event=no_overlay_found` 频繁出现，说明叠加容器定位未命中，需要进一步取证收敛具体控件容器字段，再替换 `ClearModeController` 的定位逻辑。

## 安全与边界

- 作用域最小化：仅 `com.ss.android.ugc.awemf`。
- 不 Hook `system_server`、不扩大 scope。
- 控件定位采用启发式，宁可漏判不误判，避免误伤视频本体。
- 所有 Hook 均带 `ExceptionMode.DEFAULT`（由 `module.prop` 的 `exceptionMode=protective` 兜底），单个 Hook 异常不影响模块整体。
