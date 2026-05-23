package com.yang.emperor

import androidx.compose.ui.graphics.Color

enum class ScreenRoute {
    MAIN,
    HISTORY,
    SETTINGS
}

enum class ApiMode(val value: String, val label: String) {
    IMAGES("images", "Images API"),
    RESPONSES("responses", "Responses API"),
    GENERATIONS_EDIT("generations_edit", "Generations 图生图兼容");

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
    val size: String,
    val quality: String,
    val count: String,
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
