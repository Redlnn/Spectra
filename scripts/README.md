# 开发脚本

这些工具用于维护项目，不参与应用的正常运行。以下命令均在项目根目录执行。

| 脚本                             | 用途                                             |
| -------------------------------- | ------------------------------------------------ |
| `Generate-AppIcon.ts`            | 从 `design/app-icon.svg` 生成 Android 图标和预览 |
| `Format-Kotlin.ps1`              | 格式化 Kotlin 代码和 Gradle 脚本                 |
| `Generate-OpenSourceNotices.ps1` | 更新关于页的开源许可                             |

## 图标

需要 Node.js 和 pnpm。

```powershell
pnpm --dir scripts install --frozen-lockfile
pnpm --dir scripts run check
pnpm --dir scripts run generate:icon
```

修改 SVG 后重新生成即可，预览输出到 `artifacts/icons/<版本>/`。

## 升级脚本依赖

```powershell
pnpm --dir scripts outdated
pnpm --dir scripts update --latest
pnpm --dir scripts run check
```

升级后重新生成图标确认效果，并一并提交 `package.json` 和 `pnpm-lock.yaml`。

## 格式与许可

需要 PowerShell 和 JDK。修改 Kotlin 后可检查或整理格式：

```powershell
pwsh -File scripts/Format-Kotlin.ps1 -Check
pwsh -File scripts/Format-Kotlin.ps1
```

更改应用依赖后更新开源许可：

```powershell
.\gradlew.bat :app:writeRuntimeModules
pwsh -File scripts/Generate-OpenSourceNotices.ps1
```
