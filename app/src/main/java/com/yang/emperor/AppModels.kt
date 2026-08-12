package com.yang.emperor

import androidx.compose.ui.graphics.Color

/**
 * 应用内统一配置常量。
 *
 * 集中维护所有 SharedPreferences 的存储名称与键名，避免在多个文件里散落裸字符串导致拼写不一致、
 * 静默丢配置。这里的字符串值与历史版本保持完全一致，因此升级后老用户的本地配置不会丢失。
 */
object ConfigKeys {
    // SharedPreferences 存储名称
    const val LEGACY_PREFS_NAME = "config"
    const val SECURE_PREFS_NAME = "secure_config"

    // 安全存储迁移标记
    const val SECURE_MIGRATED_FROM_V16 = "secureMigratedFromV16"

    // 配置项键名
    const val BASE_URL = "baseUrl"
    const val API_KEY = "apiKey"
    const val API_MODE = "apiMode"
    const val GENERATE_MODEL = "generateModel"
    const val EDIT_MODEL = "editModel"
    const val MODEL = "model"
    const val ONBOARDING_DONE = "onboardingDone"
    const val PROMPT = "prompt"
    const val SIZE = "size"
    const val QUALITY = "quality"
    const val OUTPUT_FORMAT = "outputFormat"
    const val BACKGROUND = "background"
    const val CUSTOM_SAVE_DIRECTORY_URI = "customSaveDirectoryUri"

    // “自动寻找生图模型”的结果持久化
    const val DISCOVERED_IMAGE_MODELS = "discoveredImageModels"

    // 历史记录
    const val HISTORY = "history"

    /** 需要在普通存储与加密存储之间迁移的配置键集合。 */
    val CONFIG_MIGRATION_KEYS = listOf(
        BASE_URL, API_KEY, API_MODE, GENERATE_MODEL, EDIT_MODEL, MODEL, HISTORY
    )
}

/** 应用级常量：仓库地址、网络超时等跨界面共享的固定值。 */
object AppConfig {
    const val REPO_URL = "https://github.com/Wzindx/ImageForge"

    /** 轻量 UI 网络请求（如开发者头像加载）的连接/读取超时，单位毫秒。 */
    const val UI_NETWORK_TIMEOUT_MS = 8000
}

enum class ScreenRoute {
    MAIN,
    HISTORY,
    SETTINGS
}

enum class ApiMode(val value: String, val label: String) {
    IMAGES("images", "Images API"),
    RESPONSES("responses", "Responses API"),
    GENERATIONS_EDIT("generations_edit", "Generations 图生图兼容"),
    ATLAS_CLOUD("atlas_cloud", "Atlas Cloud");

    companion object {
        fun from(value: String?): ApiMode =
            entries.firstOrNull { it.value == value } ?: IMAGES
    }
}

data class HistoryItem(
    val time: String,
    val mode: String,
    val model: String,
    val prompt: String,
    val path: String,
    val state: String = "success",
    val error: String = ""
)

data class ImageTask(
    val id: String,
    val time: String,
    val mode: String,
    val model: String,
    val prompt: String,
    val baseUrl: String,
    val apiKey: String,
    val apiMode: ApiMode,
    val imageBytes: ByteArray?,
    val additionalImageBytes: List<ByteArray> = emptyList(),
    val size: String,
    val quality: String,
    val outputFormat: String,
    val background: String
)

data class SizeOption(
    val value: String,
    val title: String,
    val desc: String
)

val imageModels = listOf(
    "gpt-image-1",
    "gpt-image-1.5",
    "gpt-image-2"
)

val generationSizes = listOf(
    SizeOption("1024x1024", "1:1 方图", "标准正方形，通用首选"),
    SizeOption("1536x1024", "3:2 横图", "适合封面、壁纸横构图"),
    SizeOption("1024x1536", "2:3 竖图", "适合头像、海报竖构图"),
    SizeOption("2048x1536", "4:3 横图", "经典横向比例，适合相机/封面构图"),
    SizeOption("1536x2048", "3:4 竖图", "经典竖向比例，适合人物、头像和手机阅读场景"),
    SizeOption("2048x1152", "16:9 横图", "适合横屏、桌面壁纸、视频封面"),
    SizeOption("1152x2048", "9:16 竖图", "适合手机壁纸、竖屏海报"),
    SizeOption("2048x2048", "1:1 高清方图", "更高细节，更耗时"),
    SizeOption("4096x4096", "1:1 4K 方图", "超高分辨率，适合精修"),
    SizeOption("4096x2304", "16:9 4K 横图", "适合桌面壁纸"),
    SizeOption("2304x4096", "9:16 4K 竖图", "适合手机壁纸")
)

val editSizes = listOf(
    SizeOption("1024x1024", "1:1 方图", "编辑稳定、兼容性最好"),
    SizeOption("1536x1024", "3:2 横图", "横向延展"),
    SizeOption("1024x1536", "2:3 竖图", "纵向延展"),
    SizeOption("2048x1536", "4:3 横图", "经典横向编辑比例"),
    SizeOption("1536x2048", "3:4 竖图", "经典竖向编辑比例"),
    SizeOption("2048x1152", "16:9 横图", "横屏编辑比例"),
    SizeOption("1152x2048", "9:16 竖图", "竖屏编辑比例"),
    SizeOption("2048x2048", "1:1 高清方图", "高细节编辑")
)

/**
 * Atlas Cloud 模式的宽高比选项：直接是协议支持的 aspect_ratio 值（不含像素尺寸），
 * 完整覆盖 Reve 2.1 等模型的可选比例；分辨率由服务端决定（Reve 原生 4K），
 * 请求体只发送 aspect_ratio，因此这里只让用户选比例。
 */
val atlasSizes = listOf(
    SizeOption("auto", "自动", "由模型自行决定比例"),
    SizeOption("1:1", "1:1 方图", "正方形，通用首选"),
    SizeOption("4:1", "4:1 超宽横幅", "极宽横幅，适合 Banner"),
    SizeOption("3:1", "3:1 宽横幅", "宽横幅构图"),
    SizeOption("21:9", "21:9 电影宽幅", "电影级超宽画幅"),
    SizeOption("2:1", "2:1 横图", "横向延展"),
    SizeOption("17:9", "17:9 横图", "DCI 电影比例"),
    SizeOption("16:9", "16:9 横图", "桌面壁纸、视频封面"),
    SizeOption("3:2", "3:2 横图", "相机横向构图"),
    SizeOption("4:3", "4:3 横图", "经典横向比例"),
    SizeOption("5:4", "5:4 近方横图", "大画幅相机比例"),
    SizeOption("4:5", "4:5 近方竖图", "社交平台竖图"),
    SizeOption("3:4", "3:4 竖图", "经典竖向比例"),
    SizeOption("2:3", "2:3 竖图", "相机竖向构图"),
    SizeOption("9:16", "9:16 竖图", "手机壁纸、竖屏海报"),
    SizeOption("1:2", "1:2 竖长图", "窄竖长构图"),
    SizeOption("1:3", "1:3 超长竖图", "极窄竖长构图"),
    SizeOption("1:4", "1:4 超超长竖图", "最窄竖长构图")
)

val qualityOptions = listOf("auto", "low", "medium", "high")
val outputFormats = listOf("png", "jpeg", "webp")
val backgroundOptions = listOf("auto", "transparent", "opaque")

val ratioGuide = listOf(
    "1:1 → 1024x1024 / 2048x2048 / 4096x4096",
    "3:2 → 1536x1024",
    "2:3 → 1024x1536",
    "4:3 → 2048x1536",
    "3:4 → 1536x2048",
    "16:9 → 2048x1152 / 4096x2304",
    "9:16 → 1152x2048 / 2304x4096"
)

val pageBg = Color(0xFFF4F6FB)
val cardBg = Color(0xFFF8FAFF)
val heroStart = Color(0xFF4E67A8)
val accent = Color(0xFF4B63B3)
val softAccent = Color(0xFFE8EEFF)
val successBg = Color(0xFFE9F7EF)
val successText = Color(0xFF1E7B4D)
val errorBg = Color(0xFFFFECEC)
val errorText = Color(0xFFC03B3B)
