# ImageForge

[![Latest Release](https://img.shields.io/github/v/release/Wzindx/ImageForge?label=Latest%20Release)](https://github.com/Wzindx/ImageForge/releases/latest)

ImageForge 是一个面向 Android 的轻量图像生成应用，基于 **Kotlin + Jetpack Compose + Material 3** 构建。它提供简洁的移动端创作入口，用于通过兼容接口完成文生图、参考图生成、任务管理和结果保存。

项目 README 只介绍主分支的当前能力，不按单个版本记录更新内容。版本变更、APK 资产和发布时间请以 [GitHub Releases](https://github.com/Wzindx/ImageForge/releases/latest) 为准。

## 主要功能

- 文生图：输入提示词生成图像。
- 图生图 / 参考图生成：选择参考图并结合提示词生成或编辑。
- 接口配置：支持自定义 Base URL、API Key、接口模式和模型 ID。
- 自动寻找生图模型：可根据当前接口的 `/models` 结果筛选生图相关模型，并自动填入模型配置。
- 后台任务：生成任务可在后台继续执行，并在历史记录中跟踪状态。
- 历史记录：集中查看处理中、成功和失败的任务。
- 结果操作：支持打开、保存、分享生成图片。
- 错误排查：失败详情保留完整错误，便于复制和定位接口、网络或模型问题。

## 下载与安装

请从 Releases 页面下载最新正式 APK：

```text
https://github.com/Wzindx/ImageForge/releases/latest
```

发布资产：

```text
app-release.apk
```

应用包名：

```text
com.yang.emperor
```

如果 Android 提示“未知来源应用”，请按系统提示允许当前安装来源。

## 使用说明

1. 安装并打开应用。
2. 在首次引导或设置页填写：
   - Base URL
   - API Key
   - 接口模式
   - 文生图模型 ID / 图生图模型 ID
3. 可选择使用“自动寻找生图模型”辅助填入模型。
4. 回到首页输入提示词。
5. 可选：选择参考图。
6. 点击“生成图像”。
7. 在历史记录中查看任务状态和结果。

Base URL 支持自动处理 `/v1`：

- 已以 `/v1` 结尾时，直接拼接接口路径。
- 未以 `/v1` 结尾时，应用会自动补全 `/v1`。

示例：

```text
https://example.com/v1
https://example.com
```

## 图片保存策略

ImageForge 默认不会在生成成功后自动写入系统相册。

默认流程：

1. 生成图片先保存到 App 私有目录。
2. 历史记录使用 App 内部图片副本进行预览和分享。
3. 用户在详情页点击“保存”后，才会导出到系统相册或自定义保存目录。
4. 删除系统相册中的图片，不会影响 App 内历史记录的预览。
5. 删除 ImageForge 内的历史记录时，会清理 App 私有目录中的对应图片副本。

这种方式可以减少相册污染，同时保留打开、保存和分享能力。

## 接口与模型

支持配置项：

- Base URL
- API Key
- 接口模式
- 文生图模型 ID
- 图生图模型 ID
- 图片尺寸、画质、输出格式、背景模式和生成数量

支持的接口模式包括：

- Images API
- Responses API
- Generations 兼容模式

自动寻找生图模型功能会请求当前接口的 `/models`，并只筛选与图像生成相关的模型 ID。该功能用于辅助配置模型，不会主动发起真实图片生成请求。

## 历史记录与错误信息

历史记录用于查看生成任务状态：

- 处理中
- 成功
- 失败

成功记录支持查看 Prompt、打开图片、保存图片和分享图片。

失败记录会保留完整错误详情，可滚动查看或复制，用于排查：

- Base URL 配置问题
- API Key 权限问题
- 模型 ID 不兼容
- 接口模式不匹配
- 代理、中转或网络断流
- 服务端返回异常

## 网络兼容

应用对常见代理、中转接口和不稳定网络做了兼容处理，包括连接超时、读取超时、断流重试和更明确的错误提示。

遇到 `unexpected end of stream`、`EOFException`、`connection reset`、`timeout` 等问题时，可尝试：

- 切换代理节点。
- 更换 Base URL。
- 检查接口模式是否匹配。
- 检查模型 ID 是否支持图像生成。
- 复制完整错误详情继续排查。

## 开发与构建

项目技术栈：

- Kotlin
- Jetpack Compose
- Material 3
- Android Gradle Plugin
- Gradle Wrapper
- GitHub Actions

常用命令：

```bash
# 构建 Debug APK
./gradlew :app:assembleDebug

# 构建 Release APK
./gradlew :app:assembleRelease

# 静态检查
./gradlew :app:lintDebug
```

Windows 环境可使用：

```bash
gradlew.bat :app:assembleDebug
gradlew.bat :app:assembleRelease
```

推荐使用仓库自带 Gradle Wrapper 构建，避免本机 Gradle 版本差异。

## 发布

发布流程由 GitHub Actions 处理。正式发布资产使用：

```text
app-release.apk
```

用户侧推荐始终通过以下入口下载最新版本：

```text
https://github.com/Wzindx/ImageForge/releases/latest
```

## License

本项目采用 Apache License 2.0 授权，详见仓库根目录的 `LICENSE` 文件。
