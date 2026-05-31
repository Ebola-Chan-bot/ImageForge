# ImageForge

[![Latest Release](https://img.shields.io/github/v/release/Wzindx/ImageForge?label=Latest%20Release)](https://github.com/Wzindx/ImageForge/releases/latest)

ImageForge 是一个面向 Android 的轻量图像生成应用，基于 **Kotlin + Jetpack Compose + Material 3** 构建，通过兼容 OpenAI 风格的接口完成文生图、参考图生成、任务管理与结果保存。

README 只介绍主分支当前能力，不逐版本记录更新。各版本的 APK 与变更请以 [GitHub Releases](https://github.com/Wzindx/ImageForge/releases/latest) 为准。

## 主要功能

- 文生图：输入提示词生成图像，单次生成一张，稳定可控。
- 图生图 / 参考图生成：选择参考图并结合提示词生成或编辑。
- 接口配置：自定义 Base URL、API Key、接口模式与模型 ID。
- 自动寻找生图模型：根据当前接口的 `/models` 结果筛选生图相关模型并自动填入。
- 后台任务：生成任务可在后台继续执行，并在历史记录中跟踪状态。
- 历史记录：集中查看处理中、成功与失败的任务。
- 结果操作：打开、保存、分享生成图片。
- 错误排查：失败详情保留完整错误，便于复制定位接口、网络或模型问题。

## 下载与安装

从 Releases 页面下载最新正式 APK（资产名 `app-release.apk`，包名 `com.yang.emperor`）：

```text
https://github.com/Wzindx/ImageForge/releases/latest
```

若系统提示“未知来源应用”，按提示允许当前安装来源即可。

## 使用说明

1. 安装并打开应用。
2. 在首次引导或设置页填写 Base URL、API Key、接口模式与模型 ID。
3. 可选择“自动寻找生图模型”辅助填入模型。
4. 回到首页输入提示词，可选择参考图，点击“生成图像”。
5. 在历史记录中查看任务状态与结果。

Base URL 会自动处理 `/v1`：以 `/v1` 结尾时直接拼接接口路径，否则自动补全 `/v1`。

```text
https://example.com/v1
https://example.com
```

## 图片保存策略

应用默认不在生成成功后自动写入系统相册，以减少相册污染：

1. 生成图片先保存到 App 私有目录。
2. 历史记录使用 App 内部副本进行预览与分享。
3. 在详情页点击“保存”后才导出到系统相册或自定义目录。
4. 删除系统相册图片不影响 App 内历史预览。
5. 删除 App 内历史记录时，会清理私有目录中的对应副本。

## 接口与模型

可配置项：Base URL、API Key、接口模式、文生图 / 图生图模型 ID，以及图片尺寸、画质、输出格式与背景模式。

支持的接口模式：

- Images API
- Responses API
- Generations 图生图兼容

接口兼容性：

- 当网关不支持 `/images/generations`，或返回 500 / `server_error` 时，自动回退到 `/chat/completions`，解析 Markdown 图片链接或 Base64 数据。
- 对 `grok-imagine` / `imagine-image` / `image-lite` 等模型优先使用 Chat Completions 生图路径。
- 支持 HTTP 明文 Base URL，兼容内网、自建网关与临时代理。

“自动寻找生图模型”只请求 `/models` 并筛选图像生成相关模型 ID，用于辅助配置，不会发起真实生成请求。

## 历史记录与错误信息

历史记录区分处理中、成功、失败三种状态。成功记录支持查看 Prompt、打开 / 保存 / 分享图片；失败记录保留完整错误详情，可滚动查看或复制，便于排查 Base URL、API Key、模型 ID、接口模式、代理网络或服务端异常等问题。

## 网络兼容

应用对常见代理、中转接口与不稳定网络做了兼容（连接 / 读取超时、断流重试、明确错误提示）。遇到 `unexpected end of stream`、`EOFException`、`connection reset`、`timeout` 等问题时，可尝试切换代理节点、更换 Base URL、核对接口模式、确认模型 ID 是否支持图像生成，并复制完整错误详情继续排查。

## 开发与构建

技术栈：Kotlin、Jetpack Compose、Material 3、Android Gradle Plugin、Gradle Wrapper。

```bash
# Debug APK
./gradlew :app:assembleDebug

# Release APK
./gradlew :app:assembleRelease

# 静态检查
./gradlew :app:lintDebug
```

Windows 使用 `gradlew.bat` 替代 `./gradlew`。推荐使用仓库自带 Gradle Wrapper 构建，避免本机版本差异。

## 参考与致谢

本项目在界面风格、交互组织与文档表达上参考了以下开源项目，感谢其开发者的启发：

- [compose-miuix-ui / miuix](https://github.com/compose-miuix-ui/miuix)：面向移动端的 Compose 视觉风格、圆角卡片与分组体验。
- [ReChronoRain / HyperCeiler](https://github.com/ReChronoRain/HyperCeiler)：README 说明结构与致谢组织方式。
- [CookSleep / gpt_image_playground](https://github.com/CookSleep/gpt_image_playground)：图像生成工作流与轻量化 Playground 方向。

ImageForge 是独立项目，不属于上述项目的官方、衍生或合作项目，当前未直接包含其源码；名称与链接仅用于说明参考来源与开源致谢。

## License

本项目采用 Apache License 2.0 授权，详见仓库根目录的 `LICENSE` 文件。