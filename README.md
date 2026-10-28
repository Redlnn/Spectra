# 声镜 · Spectra

一款 Android 音频分析应用，用波形、频谱和声谱图观察声音。

## 功能

- 麦克风输入和信号发生器，支持音高检测。
- 波形、频谱、声谱图自由组合，支持缩放、光标和峰值标记。
- 导出图表图片、频谱数据及最近的音频。

## 使用

需要 Android 8.0 或更新版本。打开应用后选择输入来源，使用麦克风时需允许录音权限；没有麦克风也可以使用信号发生器。

右上角可暂停采集、导出和打开设置。点击电平卡片可切换 dBFS / dB（+100），其中 dB 只是显示偏移，不是校准后的声压级。

## 开发

使用 Android Studio 打开项目，安装 Android SDK 37，并使用 Android Studio 自带的 JDK。项目采用 Kotlin 和 Jetpack Compose。

### 编译打包

在项目根目录执行：

```powershell
.\gradlew.bat :app:assembleDebug                      # 调试 APK
.\gradlew.bat :app:assembleRelease                    # Release APK
.\gradlew.bat :app:bundleRelease                      # 发布用 AAB
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug    # 单元测试与代码检查
```

产物位于 `app/build/outputs/`，其中 `apk/` 存放 APK，`bundle/` 存放 AAB。当前 release 使用调试签名，正式发布前需配置自己的签名。

### 升级依赖

- Android 依赖与 AGP 版本：修改 `gradle/libs.versions.toml`。
- Kotlin Compose 编译插件：修改 `app/build.gradle.kts` 中的插件版本。
- Gradle：修改 `gradle/wrapper/gradle-wrapper.properties`，同步更新分发包版本与 SHA-256。

修改后在 Android Studio 同步 Gradle，再执行上面的构建与检查命令。应用运行依赖变化后，还需重新生成开源许可。

脚本依赖通过 pnpm 升级，具体命令以及图标、格式、许可工具的用法见 [开发脚本](scripts/README.md)。

## 开源

项目仓库：[Redlnn/Spectra](https://github.com/Redlnn/Spectra)。本项目使用 [AGPL-3.0](LICENSE) 许可，第三方组件保留各自的许可。
