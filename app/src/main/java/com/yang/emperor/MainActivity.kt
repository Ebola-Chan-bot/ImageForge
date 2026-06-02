package com.yang.emperor

import android.Manifest
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import com.yang.emperor.ui.theme.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.util.UUID
import java.net.URL
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.io.IOException

private object ImageForgeBackgroundRunner {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}

private fun copyTextToClipboard(context: Context, label: String, text: String, toastText: String = "已复制到剪贴板") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
}

/** 判断一条提示是否属于错误/失败类，用于决定配色与显示时长。 */
fun isErrorNotice(text: String): Boolean =
    text.contains("失败") || text.contains("错误") || text.contains("HTTP")

/**
 * 提示自动消失的时长（毫秒）：
 * 错误类提示保留更久（6 秒）便于看清原因，普通成功/操作反馈较短（2 秒）。
 * 失败的完整原因仍会保留在历史记录中，可随时查看。
 */
fun noticeDisplayMillis(text: String): Long =
    if (isErrorNotice(text)) 6000L else 2000L

private fun compactErrorMessage(message: String): String {
    return maskSensitiveInfo(message).lines().firstOrNull { it.isNotBlank() }?.take(140) ?: "未知错误"
}

private fun detailedTaskErrorMessage(e: Exception, task: ImageTask): String {
    val rawStack = maskSensitiveInfo(e.stackTraceToString())
    val taskInfo = buildString {
        appendLine("任务信息：")
        appendLine("- 模式：${task.mode}")
        appendLine("- 接口模式：${task.apiMode.label}")
        appendLine("- Base URL：${maskSensitiveInfo(task.baseUrl)}")
        appendLine("- 模型：${task.model}")
        appendLine("- 尺寸：${task.size}")
        appendLine("- 质量：${task.quality}")
        appendLine("- 输出格式：${task.outputFormat}")
        appendLine("- Prompt：${task.prompt}")
    }
    val chain = generateSequence(e as Throwable?) { it.cause }.toList()
    val root = chain.lastOrNull() ?: e
    val rootName = root.javaClass.simpleName
    val rootMessage = root.message.orEmpty().ifBlank { e.message.orEmpty() }

    val searchable = (listOf(rootMessage, e.message.orEmpty(), rawStack) + chain.map { it.message.orEmpty() })
        .joinToString("\n")

    val httpCode = Regex("""\b(401|403|404|408|409|422|429|500|502|503|504|524)\b""")
        .find(searchable)
        ?.value

    if (httpCode != null) {
        val meaning = when (httpCode) {
            "401" -> "认证失败，API Key 无效、缺失或权限不足"
            "403" -> "请求被拒绝，账号、模型或接口权限不足"
            "404" -> "接口不存在，Base URL、接口模式或模型路径不匹配"
            "408" -> "请求超时，服务端未在限定时间内返回"
            "409" -> "请求冲突，服务端拒绝当前任务状态"
            "422" -> "请求参数无法处理，模型、尺寸、格式或提示词可能不被支持"
            "429" -> "请求过多或额度不足，服务端限流"
            "500" -> "服务端内部错误"
            "502" -> "网关错误，上游服务异常"
            "503" -> "服务端暂时不可用或正在维护"
            "504" -> "网关超时，上游服务响应过慢"
            "524" -> "Cloudflare 等待源站超时，通常是中转网关已连到源站但源站 120 秒内未返回结果"
            else -> "HTTP 错误"
        }
        val body = rootMessage.ifBlank { rawStack.lines().firstOrNull { it.isNotBlank() }.orEmpty() }
        return "HTTP $httpCode：$meaning\n$body\n\n$taskInfo\n完整异常堆栈：\n$rawStack"
    }

    val networkHint = when (root) {
        is SocketTimeoutException -> "SocketTimeoutException：请求超时，接口在限定时间内没有返回结果。"
        is UnknownHostException -> "UnknownHostException：无法解析 Base URL 的域名，请检查地址或网络。"
        is IOException -> {
            if (searchable.contains("unexpected end of stream", ignoreCase = true) ||
                searchable.contains("EOFException", ignoreCase = true) ||
                searchable.contains("\\n not found: size=0", ignoreCase = true)
            ) {
                "IOException：网络连接在读取响应时提前断开，可能是服务端/代理/网关返回空响应或 HTTP/1.1 连接复用异常。"
            } else {
                "IOException：${rootMessage.ifBlank { "网络或文件读写异常" }}"
            }
        }
        else -> "$rootName：${rootMessage.ifBlank { "无详细异常消息" }}"
    }

    return "$networkHint\n\n$taskInfo\n完整异常堆栈：\n$rawStack"
}


private fun readableSaveDirectoryLabel(uriString: String): String {
    if (uriString.isBlank()) return "/storage/emulated/0/Pictures/ImageForge"

    val decoded = Uri.decode(uriString)
    val primaryMarker = "tree/primary:"
    val primaryIndex = decoded.indexOf(primaryMarker)
    if (primaryIndex >= 0) {
        val relativePath = decoded.substring(primaryIndex + primaryMarker.length).trim('/')
        return if (relativePath.isBlank()) "/storage/emulated/0" else "/storage/emulated/0/$relativePath"
    }

    val documentMarker = "document/primary:"
    val documentIndex = decoded.indexOf(documentMarker)
    if (documentIndex >= 0) {
        val relativePath = decoded.substring(documentIndex + documentMarker.length).trim('/')
        return if (relativePath.isBlank()) "/storage/emulated/0" else "/storage/emulated/0/$relativePath"
    }

    return decoded
        .removePrefix("content://com.android.externalstorage.documents/tree/primary%3A")
        .removePrefix("content://com.android.externalstorage.documents/tree/primary:")
        .ifBlank { uriString }
}


class MainActivity : ComponentActivity() {
    private val activityTaskScope = ImageForgeBackgroundRunner.scope
    private var notificationImageUri by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationImageUri = intent?.getStringExtra("image_uri").orEmpty()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                AndroidColor.rgb(244, 246, 255),
                AndroidColor.rgb(244, 246, 255)
            )
        )
        setContent {
            AppTheme {
                MainScreen(
                    activityTaskScope = activityTaskScope,
                    notificationImageUri = notificationImageUri,
                    onNotificationImageHandled = { notificationImageUri = "" }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationImageUri = intent.getStringExtra("image_uri").orEmpty()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    activityTaskScope: CoroutineScope,
    notificationImageUri: String = "",
    onNotificationImageHandled: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { secureConfigPreferences(context) }

    var currentRoute by rememberSaveable { mutableStateOf(ScreenRoute.MAIN) }

    var baseUrl by rememberSaveable { mutableStateOf(prefs.getString(ConfigKeys.BASE_URL, "") ?: "") }
    var apiKey by rememberSaveable { mutableStateOf(prefs.getString(ConfigKeys.API_KEY, "") ?: "") }
    var apiMode by rememberSaveable { mutableStateOf(ApiMode.from(prefs.getString(ConfigKeys.API_MODE, ApiMode.IMAGES.value))) }
    var generateModel by rememberSaveable { mutableStateOf(prefs.getString(ConfigKeys.GENERATE_MODEL, prefs.getString(ConfigKeys.MODEL, "gpt-image-1")) ?: "gpt-image-1") }
    var editModel by rememberSaveable { mutableStateOf(prefs.getString(ConfigKeys.EDIT_MODEL, "gpt-image-1") ?: "gpt-image-1") }
    var customGenerateModel by rememberSaveable { mutableStateOf(generateModel) }
    var customEditModel by rememberSaveable { mutableStateOf(editModel) }
    var prompt by rememberSaveable { mutableStateOf(prefs.getString(ConfigKeys.PROMPT, "") ?: "") }
    var size by rememberSaveable { mutableStateOf(prefs.getString(ConfigKeys.SIZE, "1024x1024") ?: "1024x1024") }
    var quality by rememberSaveable { mutableStateOf(prefs.getString(ConfigKeys.QUALITY, "auto") ?: "auto") }
    var outputFormat by rememberSaveable { mutableStateOf(prefs.getString(ConfigKeys.OUTPUT_FORMAT, "png") ?: "png") }
    var background by rememberSaveable { mutableStateOf(prefs.getString(ConfigKeys.BACKGROUND, "auto") ?: "auto") }
    var editMode by rememberSaveable { mutableStateOf(false) }
    var selectedImage by remember { mutableStateOf(null as Uri?) }
    var selectedImageBytes by remember { mutableStateOf(null as ByteArray?) }
    var isReadingReferenceImage by remember { mutableStateOf(false) }
    var showReferenceSheet by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }
    var discoveredImageModels by remember { mutableStateOf(emptyList<String>()) }
    var pendingDiscoveredImageModels by remember { mutableStateOf(emptyList<String>()) }
    var showDiscoveredModelPicker by remember { mutableStateOf(false) }
    var isDiscoveringImageModels by remember { mutableStateOf(false) }
    var showParamsSheet by remember { mutableStateOf(false) }

    var status by remember { mutableStateOf("") }
    var historyNotice by remember { mutableStateOf("") }
    var settingsNotice by remember { mutableStateOf("") }
    var imageBytes by remember { mutableStateOf(null as ByteArray?) }
    var previewImages by remember { mutableStateOf(emptyList<ByteArray>()) }
    var selectedPreviewIndex by remember { mutableIntStateOf(0) }
    var previewPrompt by remember { mutableStateOf("") }
    var previewSavedPath by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(loadHistory(prefs)) }
    var previewHistoryItem by remember { mutableStateOf(null as HistoryItem?) }
    val selectedHistoryKeys = remember { mutableStateListOf<String>() }
    var showAdvancedOptions by rememberSaveable { mutableStateOf(false) }
    val shouldShowInitialOnboarding = remember {
        !prefs.getBoolean(ConfigKeys.ONBOARDING_DONE, false) && (apiKey.isBlank() || baseUrl.isBlank())
    }
    var showOnboarding by remember { mutableStateOf(shouldShowInitialOnboarding) }
    var onboardingReturnRoute by remember { mutableStateOf(ScreenRoute.MAIN.name) }
    var onboardingSessionId by remember { mutableLongStateOf(0L) }
    val runningTasks = remember { mutableStateListOf<String>() }
    val runningTaskJobs = remember { mutableMapOf<String, Job>() }
    val cancelledTaskIds = remember { mutableStateListOf<String>() }
    var customSaveDirectoryUriString by rememberSaveable { mutableStateOf(prefs.getString(ConfigKeys.CUSTOM_SAVE_DIRECTORY_URI, "") ?: "") }
    val customSaveDirectoryUri = customSaveDirectoryUriString.takeIf { it.isNotBlank() }?.let { it.toUri() }
    val saveDirectoryLabel = readableSaveDirectoryLabel(customSaveDirectoryUriString)

    fun applyDiscoveredImageModel(selectedModel: String, models: List<String>) {
        discoveredImageModels = models
        generateModel = selectedModel
        editModel = selectedModel
        customGenerateModel = selectedModel
        customEditModel = selectedModel
        prefs.edit {
            putString(ConfigKeys.GENERATE_MODEL, selectedModel)
            putString(ConfigKeys.EDIT_MODEL, selectedModel)
            putString(ConfigKeys.MODEL, selectedModel)
        }
    }

    fun handleDiscoveredImageModels(models: List<String>) {
        when {
            models.isEmpty() -> {
                status = "未在当前接口发现生图相关模型。"
            }
            models.size == 1 -> {
                val selectedModel = models.first()
                applyDiscoveredImageModel(selectedModel, models)
                status = "已发现 1 个生图模型，并自动替换为 $selectedModel。"
            }
            else -> {
                discoveredImageModels = models
                pendingDiscoveredImageModels = models
                showDiscoveredModelPicker = true
                status = "已发现 ${models.size} 个生图模型，请选择要使用的模型。"
            }
        }
    }

    BackHandler(enabled = !showOnboarding && selectedHistoryKeys.isNotEmpty()) {
        selectedHistoryKeys.clear()
    }

    BackHandler(enabled = currentRoute != ScreenRoute.MAIN && !showOnboarding && selectedHistoryKeys.isEmpty()) {
        currentRoute = ScreenRoute.MAIN
    }

    val isConfigured = baseUrl.isNotBlank() && apiKey.isNotBlank()
    val runningCount = runningTasks.size

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val saveDirectoryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }
            customSaveDirectoryUriString = uri.toString()
            prefs.edit { putString(ConfigKeys.CUSTOM_SAVE_DIRECTORY_URI, customSaveDirectoryUriString) }
            settingsNotice = "图片保存路径已更新。"
        }
    }

    val currentSizes = if (editMode) editSizes else generationSizes
    val selectedSizeOption = currentSizes.firstOrNull { it.value == size } ?: currentSizes.first()
    val modelOptions = remember(discoveredImageModels) {
        (discoveredImageModels + imageModels).distinct()
    }

    LaunchedEffect(selectedImage) {
        editMode = selectedImage != null
    }

    LaunchedEffect(editMode) {
        if (currentSizes.none { it.value == size }) {
            size = currentSizes.first().value
        }
    }

    LaunchedEffect(settingsNotice) {
        if (settingsNotice.isNotBlank()) {
            delay(noticeDisplayMillis(settingsNotice))
            settingsNotice = ""
        }
    }

    LaunchedEffect(status) {
        if (status.isNotBlank()) {
            delay(noticeDisplayMillis(status))
            status = ""
        }
    }

    LaunchedEffect(historyNotice) {
        if (historyNotice.isNotBlank()) {
            delay(noticeDisplayMillis(historyNotice))
            historyNotice = ""
        }
    }

    LaunchedEffect(notificationImageUri, history) {
        if (notificationImageUri.isNotBlank()) {
            val target = history.firstOrNull {
                it.state == "success" && it.path == notificationImageUri
            }
            if (target != null) {
                currentRoute = ScreenRoute.HISTORY
                previewHistoryItem = target
                historyNotice = "已打开生成结果。"
            } else {
                currentRoute = ScreenRoute.HISTORY
                historyNotice = "生成结果已保存，请在图片记录中查看。"
            }
            onNotificationImageHandled()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedImage = uri
        selectedImageBytes = null

        if (uri == null) {
            isReadingReferenceImage = false
            status = ""
            activityTaskScope.launch(Dispatchers.IO) {
                clearReferenceImageCache(context)
            }
            return@rememberLauncherForActivityResult
        }

        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        showReferenceSheet = false
        isReadingReferenceImage = true
        status = "正在读取并缓存参考图..."

        activityTaskScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    clearReferenceImageCache(context)
                    cacheReferenceImageBytes(context, uri)
                }
            }

            if (selectedImage != uri) {
                return@launch
            }

            result
                .onSuccess { bytes ->
                    selectedImageBytes = bytes
                    status = "参考图已读取并缓存，将用于下一次图生图。"
                }
                .onFailure {
                    selectedImage = null
                    selectedImageBytes = null
                    withContext(Dispatchers.IO) {
                        clearReferenceImageCache(context)
                    }
                    status = "参考图读取失败：${friendlyShortErrorMessage(it)}，请重新选择。"
                }

            isReadingReferenceImage = false
        }
    }


    fun cancelRunningImageTasks() {
        if (runningTasks.isEmpty()) return

        val taskIds = runningTasks.toList()
        taskIds.forEach { taskId ->
            if (taskId !in cancelledTaskIds) {
                cancelledTaskIds.add(taskId)
            }
            cancelImageRequest(taskId)
            runningTaskJobs[taskId]?.cancel(CancellationException("用户已取消生成图像"))
        }

        history = history.filterNot { item ->
            item.state == "running"
        }
        saveHistory(prefs, history)
        runningTasks.clear()
        runningTaskJobs.clear()
        historyNotice = "已取消生成图像，并已中止后台请求。"
        status = "已取消生成图像，并已中止后台请求。"
    }

    fun cancelHistoryRunningItem(item: HistoryItem) {
        if (item.state != "running") return

        val matchingTaskIds = runningTasks.filter { taskId ->
            runningTaskJobs.containsKey(taskId)
        }

        matchingTaskIds.forEach { taskId ->
            if (taskId !in cancelledTaskIds) {
                cancelledTaskIds.add(taskId)
            }
            cancelImageRequest(taskId)
            runningTaskJobs[taskId]?.cancel(CancellationException("用户已从图片记录取消生成"))
        }

        history = history.filterNot { historyItem ->
            historyItem.time == item.time && historyItem.prompt == item.prompt && historyItem.state == "running"
        }
        saveHistory(prefs, history)
        previewHistoryItem = null

        if (matchingTaskIds.isNotEmpty()) {
            matchingTaskIds.forEach { taskId ->
                runningTasks.remove(taskId)
                runningTaskJobs.remove(taskId)
            }
            historyNotice = "已取消该处理中任务，并已中止当前后台请求。"
            status = "已取消该处理中任务，并已中止当前后台请求。"
        } else {
            historyNotice = "已清理重启后遗留的处理中记录。"
            status = "已清理重启后遗留的处理中记录。"
        }
    }

    fun cancelAllHistoryRunningItems() {
        val runningHistory = history.filter { it.state == "running" }
        if (runningHistory.isEmpty()) return

        val taskIds = runningTasks.toList()
        taskIds.forEach { taskId ->
            if (taskId !in cancelledTaskIds) {
                cancelledTaskIds.add(taskId)
            }
            cancelImageRequest(taskId)
            runningTaskJobs[taskId]?.cancel(CancellationException("用户已从图片记录取消全部生成"))
        }

        history = history.filterNot { it.state == "running" }
        saveHistory(prefs, history)
        runningTasks.clear()
        runningTaskJobs.clear()
        previewHistoryItem = null

        if (taskIds.isNotEmpty()) {
            historyNotice = "已取消全部处理中任务，并已中止当前后台请求。"
            status = "已取消全部处理中任务，并已中止当前后台请求。"
        } else {
            historyNotice = "已清理重启后遗留的全部处理中记录。"
            status = "已清理重启后遗留的全部处理中记录。"
        }
    }

    fun startBackgroundTask(task: ImageTask) {
        val job = activityTaskScope.launch {
            runningTasks.add(task.id)
            runningTaskJobs[task.id] = coroutineContext[Job] ?: return@launch
            val runningItem = HistoryItem(
                time = task.time,
                mode = task.mode,
                model = task.model,
                prompt = task.prompt,
                path = "后台处理中",
                state = "running"
            )
            history = listOf(runningItem) + history.take(49)
            saveHistory(prefs, history)
            try {
                val result = withContext(Dispatchers.IO) {
                    if (task.imageBytes != null) {
                        when (task.apiMode) {
                            ApiMode.IMAGES -> listOf(callEdit(
                                baseUrl = task.baseUrl,
                                apiKey = task.apiKey,
                                model = task.model,
                                prompt = task.prompt,
                                imageBytes = task.imageBytes,
                                size = task.size,
                                quality = task.quality,
                                outputFormat = task.outputFormat,
                                background = task.background,
                                requestId = task.id
                            ))
                            ApiMode.RESPONSES -> listOf(callEditResponses(
                                baseUrl = task.baseUrl,
                                apiKey = task.apiKey,
                                model = task.model,
                                prompt = task.prompt,
                                imageBytes = task.imageBytes,
                                size = task.size,
                                quality = task.quality,
                                outputFormat = task.outputFormat,
                                background = task.background,
                                requestId = task.id
                            ))
                            ApiMode.GENERATIONS_EDIT -> listOf(callEditGenerationsCompat(
                                model = task.model,
                                prompt = task.prompt,
                                imageBytes = task.imageBytes,
                                baseUrl = task.baseUrl,
                                apiKey = task.apiKey,
                                size = task.size,
                                quality = task.quality,
                                requestId = task.id
                            ))
                        }
                    } else {
                        callGenerateImages(
                            baseUrl = task.baseUrl,
                            apiKey = task.apiKey,
                            model = task.model,
                            prompt = task.prompt,
                            size = task.size,
                            quality = task.quality,
                            requestId = task.id
                        )
                    }
                }
                if (task.id !in runningTasks || task.id in cancelledTaskIds) {
                    return@launch
                }

                val results = result.filter { it.isNotEmpty() }
                val firstResult = results.firstOrNull() ?: error("图片生成接口未返回有效图片数据")
                imageBytes = firstResult
                previewImages = results
                selectedPreviewIndex = 0
                previewPrompt = task.prompt
                if (task.id !in runningTasks || task.id in cancelledTaskIds) {
                    return@launch
                }

                val savedUris = mutableListOf<String>()
                var saveError: Throwable? = null
                results.forEach { bytes ->
                    val savedResult = runCatching {
                        saveImageToAppFiles(context, bytes, task.outputFormat)
                    }
                    val savedUri = savedResult.getOrNull().orEmpty()
                    if (savedUri.startsWith("content://")) {
                        savedUris.add(savedUri)
                    } else if (saveError == null) {
                        saveError = savedResult.exceptionOrNull()
                    }
                }
                val firstSavedUri = savedUris.firstOrNull().orEmpty()
                previewSavedPath = firstSavedUri

                if (savedUris.isNotEmpty()) {
                    val successItems = savedUris.mapIndexed { index, uri ->
                        HistoryItem(
                            time = if (index == 0) task.time else "${task.time} #${index + 1}",
                            mode = task.mode,
                            model = task.model,
                            prompt = if (savedUris.size > 1) "${task.prompt}\n\n第 ${index + 1}/${savedUris.size} 张" else task.prompt,
                            path = uri,
                            state = "success"
                        )
                    }
                    history = successItems + history.filterNot {
                        it.time == task.time && it.prompt == task.prompt && it.state == "running"
                    }
                    history = history.take(50)
                    saveHistory(prefs, history)
                    historyNotice = if (savedUris.size > 1) {
                        "后台任务完成，已保存 ${savedUris.size} 张图片到应用记录。"
                    } else {
                        "后台任务完成，已保存到应用记录；需要相册文件时请手动点击保存。"
                    }
                    notifyImageReady(context, firstSavedUri)
                } else {
                    val detailedError = "图片生成成功，但写入应用内部图片记录失败：${saveError?.let { friendlyShortErrorMessage(it) } ?: "未获得可读取的图片 URI"}"
                    history = history.map {
                        if (it.time == task.time && it.prompt == task.prompt && it.state == "running") {
                            it.copy(path = "图片文件缺失", state = "failed", error = detailedError)
                        } else it
                    }
                    saveHistory(prefs, history)
                    historyNotice = detailedError
                }
            } catch (e: CancellationException) {
                cancelImageRequest(task.id)
                if (task.id !in cancelledTaskIds) {
                    cancelledTaskIds.add(task.id)
                }
                history = history.filterNot {
                    it.time == task.time && it.prompt == task.prompt && it.state == "running"
                }
                saveHistory(prefs, history)
            } catch (e: Exception) {
                if (task.id in cancelledTaskIds || task.id !in runningTasks) {
                    return@launch
                }
                val detailedError = detailedTaskErrorMessage(e, task)
                history = history.map {
                    if (it.time == task.time && it.prompt == task.prompt && it.state == "running") {
                        it.copy(path = "失败", state = "failed", error = detailedError)
                    } else it
                }
                saveHistory(prefs, history)
                historyNotice = "后台任务失败：${compactErrorMessage(detailedError)}"
            } finally {
                runningTasks.remove(task.id)
                runningTaskJobs.remove(task.id)
                cancelImageRequest(task.id)
                if (task.imageBytes != null) {
                    selectedImage = null
                    selectedImageBytes = null
                    isReadingReferenceImage = false
                    withContext(Dispatchers.IO) {
                        clearReferenceImageCache(context)
                    }
                }
            }
            delay(100)
        }
    }

    if (showOnboarding) {
        key(onboardingSessionId) {
            OnboardingScreen(
                baseUrl = baseUrl,
                apiKey = apiKey,
                onBaseUrlChange = { baseUrl = it },
                onApiKeyChange = { apiKey = it },
                onSkip = {
                    prefs.edit { putBoolean(ConfigKeys.ONBOARDING_DONE, true) }
                    showOnboarding = false
                    currentRoute = runCatching { ScreenRoute.valueOf(onboardingReturnRoute) }.getOrDefault(ScreenRoute.MAIN)
                },
                onSave = {
                    prefs.edit {
                        putString(ConfigKeys.BASE_URL, baseUrl.trim())
                        putString(ConfigKeys.API_KEY, apiKey.trim())
                        putBoolean(ConfigKeys.ONBOARDING_DONE, true)
                    }
                    settingsNotice = "接口信息已保存。"
                    status = ""
                    showOnboarding = false
                    currentRoute = runCatching { ScreenRoute.valueOf(onboardingReturnRoute) }.getOrDefault(ScreenRoute.MAIN)
                }
            )
        }
        return
    }

    if (showDiscoveredModelPicker) {
        AlertDialog(
            onDismissRequest = {
                showDiscoveredModelPicker = false
                pendingDiscoveredImageModels = emptyList()
            },
            title = { Text("选择生图模型") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "当前接口发现 ${pendingDiscoveredImageModels.size} 个生图相关模型，请选择一个写入接口与模型配置。",
                        color = Color(0xFF6B7280)
                    )
                    pendingDiscoveredImageModels.forEach { modelId ->
                        TextButton(
                            onClick = {
                                applyDiscoveredImageModel(modelId, pendingDiscoveredImageModels)
                                showDiscoveredModelPicker = false
                                pendingDiscoveredImageModels = emptyList()
                                status = "已选择并替换生图模型为 $modelId。"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = modelId,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        showDiscoveredModelPicker = false
                        pendingDiscoveredImageModels = emptyList()
                    }
                ) {
                    Text("取消")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showReferenceSheet) {
        AlertDialog(
            onDismissRequest = { showReferenceSheet = false },
            title = { Text("参考图") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "选择参考图后会自动切换为图生图 / 编辑；清除后自动回到文生图",
                        color = Color(0xFF6B7280)
                    )
                    if (selectedImage != null) {
                        StatusCard(
                            if (selectedImageBytes != null)
                                "当前参考图：${selectedImage?.lastPathSegment ?: "已选择图片"}"
                            else
                                "已记录参考图 URI，但图片缓存读取失败，请重新选择"
                        )
                    } else {
                        Text("当前未选择参考图，将使用文生图模式。", color = Color(0xFF6B7280))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picker.launch(arrayOf("image/*")) }) {
                    Text(if (selectedImage == null) "选择参考图" else "更换参考图")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = selectedImage != null || selectedImageBytes != null,
                    onClick = {
                        selectedImage = null
                        selectedImageBytes = null
                        showReferenceSheet = false
                        activityTaskScope.launch(Dispatchers.IO) {
                            clearReferenceImageCache(context)
                        }
                        status = "已清除参考图，将自动使用文生图模式。"
                    }
                ) {
                    Text("清除参考图")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showModelSheet) {
        AppBottomSheetPanel(
            title = "接口与模型",
            description = "修改后会自动保存。",
            onDismiss = { showModelSheet = false }
        ) {
            AppDropdownField(
                title = "接口模式",
                selected = apiMode.label,
                options = ApiMode.entries.map { it.label },
                onSelected = { label ->
                    ApiMode.entries.firstOrNull { it.label == label }?.let { apiMode = it }
                }
            )
            AppEditableDropdownField(
                title = "文生图模型 ID",
                value = customGenerateModel,
                options = modelOptions,
                placeholder = "可手动输入，也可从推荐模型中选择",
                onValueChange = {
                    customGenerateModel = it
                    generateModel = it
                },
                onSelected = {
                    customGenerateModel = it
                    generateModel = it
                }
            )
            AppEditableDropdownField(
                title = "图生图模型 ID",
                value = customEditModel,
                options = modelOptions,
                placeholder = "可手动输入，也可从推荐模型中选择",
                onValueChange = {
                    customEditModel = it
                    editModel = it
                },
                onSelected = {
                    customEditModel = it
                    editModel = it
                }
            )
            Button(
                enabled = !isDiscoveringImageModels,
                onClick = {
                    if (baseUrl.isBlank() || apiKey.isBlank()) {
                        status = "请先填写 Base URL 和 API Key。"
                        return@Button
                    }
                    isDiscoveringImageModels = true
                    status = "正在自动寻找生图模型..."
                    activityTaskScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { discoverImageModels(baseUrl.trim(), apiKey.trim()) }
                        }
                        isDiscoveringImageModels = false
                        result.onSuccess { models ->
                            handleDiscoveredImageModels(models)
                        }.onFailure { error ->
                            status = "自动寻找模型失败：${friendlyShortErrorMessage(error)}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isDiscoveringImageModels) "正在寻找..." else "自动寻找生图模型")
            }
            Button(
                onClick = {
                    prefs.edit {
                        putString(ConfigKeys.API_MODE, apiMode.value)
                        putString(ConfigKeys.GENERATE_MODEL, generateModel.trim())
                        putString(ConfigKeys.EDIT_MODEL, editModel.trim())
                        putString(ConfigKeys.MODEL, generateModel.trim())
                    }
                    status = "接口与模型已保存，将用于下一次生成。"
                    showModelSheet = false
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("完成")
            }
        }
    }

    previewHistoryItem?.let { item ->
        var showPromptInPreview by remember(item.time, item.prompt) { mutableStateOf(false) }
        val hasImageUri = item.state == "success" && item.path.startsWith("content://")
        val previewBitmap by produceState<Bitmap?>(initialValue = null, key1 = item.path, key2 = item.state) {
            value = if (hasImageUri) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(item.path.toUri())?.use { input ->
                            BitmapFactory.decodeStream(input)
                        }
                    }.getOrNull()
                }
            } else {
                null
            }
        }
        val canUseImageActions = hasImageUri && previewBitmap != null
        val dialogTitle = when (item.state) {
            "success" -> "图片预览"
            "running" -> "处理中"
            "failed" -> "处理失败"
            else -> "图片记录"
        }

        AlertDialog(
            onDismissRequest = { previewHistoryItem = null },
            title = { Text(dialogTitle) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 620.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (item.state) {
                        "success" -> {
                            if (previewBitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color.White)
                                        .border(1.dp, Color(0xFFD9E1F5), RoundedCornerShape(20.dp))
                                        .padding(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 520.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Image(
                                            bitmap = previewBitmap!!.asImageBitmap(),
                                            contentDescription = "生成图片预览",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(
                                                    previewBitmap!!.width.toFloat() / previewBitmap!!.height.toFloat()
                                                )
                                        )
                                    }
                                }
                                AutoDismissStatusCard("图片已生成，可在此查看、打开或分享；长图支持继续下滑查看到底部。")
                            } else {
                                StatusCard("这条记录没有可读取的图片文件，仅保留描述内容。")
                            }
                        }
                        "running" -> {
                            StatusCard("图片仍在处理中，可在此直接取消。当前进程内的后台请求会被中止；重启后遗留记录会从本地清理。")
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Button(
                                onClick = { cancelHistoryRunningItem(item) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE11D48),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("取消生成")
                            }
                        }
                        "failed" -> {
                            StatusCard("处理失败，详细原因可复制后排查。")
                        }
                    }

                    if (item.model.isNotBlank()) {
                        Text("模型：${item.model}", fontWeight = FontWeight.Bold)
                    }
                    Text("时间：${item.time}", color = Color(0xFF6B7280))

                    if (item.prompt.isNotBlank()) {
                        Button(
                            onClick = { showPromptInPreview = !showPromptInPreview },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (showPromptInPreview) "隐藏描述内容" else "查看描述内容")
                        }
                        if (showPromptInPreview) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = {
                                            copyTextToClipboard(context, "ImageForge Prompt", item.prompt, "描述词已复制")
                                            historyNotice = "描述词已复制。"
                                        }
                                    ),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = item.prompt,
                                    modifier = Modifier
                                        .padding(14.dp)
                                        .heightIn(max = 220.dp)
                                        .verticalScroll(rememberScrollState()),
                                    color = Color(0xFF4B5563),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    if (canUseImageActions) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    historyNotice = if (openImageFromHistory(context, item.path)) "已打开图片。" else "图片打开失败。"
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("打开")
                            }
                            TextButton(
                                onClick = {
                                    val saved = runCatching {
                                        saveExistingImageToGallery(context, item.path, customSaveDirectoryUri)
                                    }.getOrElse {
                                        historyNotice = "保存失败：${friendlyShortErrorMessage(it)}"
                                        return@TextButton
                                    }
                                    historyNotice = "已保存到相册：$saved"
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("保存")
                            }
                            TextButton(
                                onClick = {
                                    historyNotice = if (shareImageFromHistory(context, item.path)) "已打开系统分享。" else "分享失败。"
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("分享")
                            }
                        }
                    }

                    if (item.state == "failed" && item.error.isNotBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFFFF1F2),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(14.dp)
                                    .heightIn(max = 320.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("失败原因", fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                                SelectionContainer {
                                    Text(
                                        text = item.error,
                                        color = Color(0xFF7F1D1D),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        TextButton(
                            onClick = {
                                copyTextToClipboard(context, "ImageForge Error", item.error)
                                historyNotice = "错误详情已复制。"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("复制完整错误详情")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { previewHistoryItem = null }) { Text("关闭") }
            },
            dismissButton = {},
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showParamsSheet) {
        AppBottomSheetPanel(
            title = "生成参数",
            description = "尺寸、画质和输出格式会用于下一次生成。",
            onDismiss = { showParamsSheet = false }
        ) {
            AppDropdownField(
                title = "尺寸 / 比例",
                selected = selectedSizeOption.title + " · " + selectedSizeOption.value,
                options = currentSizes.map { "${it.title} · ${it.value}" },
                onSelected = { display ->
                    currentSizes.firstOrNull {
                        "${it.title} · ${it.value}" == display
                    }?.let {
                        size = it.value
                        prefs.edit { putString(ConfigKeys.SIZE, it.value) }
                    }
                }
            )
            AppDropdownField(
                title = "画质",
                selected = quality,
                options = qualityOptions,
                onSelected = {
                    quality = it
                    prefs.edit { putString(ConfigKeys.QUALITY, it) }
                }
            )
            AppDropdownField(
                title = "输出格式",
                selected = outputFormat,
                options = outputFormats,
                onSelected = {
                    outputFormat = it
                    prefs.edit { putString(ConfigKeys.OUTPUT_FORMAT, it) }
                }
            )
            AppDropdownField(
                title = "背景",
                selected = background,
                options = backgroundOptions,
                onSelected = {
                    background = it
                    prefs.edit { putString(ConfigKeys.BACKGROUND, it) }
                }
            )
            Button(
                onClick = { showParamsSheet = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("完成")
            }
        }
    }

    when (currentRoute) {
        ScreenRoute.SETTINGS -> Scaffold(
            containerColor = pageBg,
            bottomBar = {
                BottomNavigationBar(
                    currentRoute = currentRoute,
                    onRouteSelected = { currentRoute = it }
                )
            }
        ) { settingsPadding ->
            SettingsScreen(
            baseUrl = baseUrl,
            apiKey = apiKey,
            apiMode = apiMode,
            customGenerateModel = customGenerateModel,
            currentGenerateModel = generateModel,
            customEditModel = customEditModel,
            currentEditModel = editModel,
            recommendedModels = imageModels,
            onBaseUrlChange = { baseUrl = it },
            onApiKeyChange = { apiKey = it },
            onApiModeChange = { apiMode = it },
            onCustomGenerateModelChange = {
                customGenerateModel = it
                generateModel = it
            },
            onSelectGenerateModel = {
                generateModel = it
                customGenerateModel = it
            },
            onCustomEditModelChange = {
                customEditModel = it
                editModel = it
            },
            onSelectEditModel = {
                editModel = it
                customEditModel = it
            },
            saveDirectoryLabel = saveDirectoryLabel,
            onChooseSaveDirectory = {
                saveDirectoryPicker.launch(null)
            },
            settingsNotice = settingsNotice,
            onBack = { currentRoute = ScreenRoute.MAIN },
            onClearConfig = {
                prefs.edit {
                    remove(ConfigKeys.BASE_URL)
                    remove(ConfigKeys.API_KEY)
                    remove(ConfigKeys.API_MODE)
                    remove(ConfigKeys.GENERATE_MODEL)
                    remove(ConfigKeys.EDIT_MODEL)
                    remove(ConfigKeys.MODEL)
                    remove(ConfigKeys.CUSTOM_SAVE_DIRECTORY_URI)
                    remove(ConfigKeys.PROMPT)
                    remove(ConfigKeys.SIZE)
                    remove(ConfigKeys.QUALITY)
                    remove("count")
                    remove(ConfigKeys.OUTPUT_FORMAT)
                    remove(ConfigKeys.BACKGROUND)
                    remove(ConfigKeys.ONBOARDING_DONE)
                }
                baseUrl = ""
                apiKey = ""
                apiMode = ApiMode.IMAGES
                generateModel = "gpt-image-1"
                editModel = "gpt-image-1"
                customGenerateModel = generateModel
                customEditModel = editModel
                customSaveDirectoryUriString = ""
                settingsNotice = "已清除连接配置信息。"
                currentRoute = ScreenRoute.SETTINGS
            },
            onShowOnboarding = {},
            onSave = {
                prefs.edit {
                    putString(ConfigKeys.BASE_URL, baseUrl.trim())
                    putString(ConfigKeys.API_KEY, apiKey.trim())
                    putString(ConfigKeys.API_MODE, apiMode.value)
                    putString(ConfigKeys.GENERATE_MODEL, generateModel.trim())
                    putString(ConfigKeys.EDIT_MODEL, editModel.trim())
                    putString(ConfigKeys.MODEL, generateModel.trim())
                    putBoolean(ConfigKeys.ONBOARDING_DONE, true)
                }
                settingsNotice = "接口设置已保存。"
                status = ""
                currentRoute = ScreenRoute.MAIN
            },
            outerPadding = settingsPadding
        )
        }

        ScreenRoute.MAIN -> Scaffold(
            containerColor = pageBg,
            bottomBar = {
                BottomNavigationBar(
                    currentRoute = currentRoute,
                    onRouteSelected = { currentRoute = it }
                )
            }
        ) { padding ->
            CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(pageBg)
                        .padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "通用图像工坊",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "输入提示词，选图后会自动切换为图生图。",
                            color = Color(0xFF6B7280),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                item {
                    ElevatedCard(
                        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFF5F6FF)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            SectionTitle("创作", "")
                            if (status.isNotBlank()) {
                                StatusCard(status)
                            }

                            OutlinedTextField(
                                value = prompt,
                                onValueChange = {
                                    prompt = it
                                    prefs.edit { putString(ConfigKeys.PROMPT, it) }
                                },
                                label = { Text(if (editMode) "编辑指令" else "图片描述 Prompt") },
                                placeholder = {
                                    Text(
                                        if (editMode)
                                            "例如：保留主体不变，改成赛博朋克夜景，增强霓虹反射"
                                        else
                                            "例如：一只穿宇航服的橘猫，电影感灯光，超细节"
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 140.dp),
                                shape = RoundedCornerShape(20.dp)
                            )

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable(enabled = !isReadingReferenceImage) { picker.launch(arrayOf("image/*")) },
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                shape = RoundedCornerShape(20.dp),
                                tonalElevation = 1.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = if (selectedImage != null) "更换图片" else "选择图片",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        if (isReadingReferenceImage) {
                                            Text(
                                                text = "正在读取参考图，请稍候",
                                                color = Color(0xFF6B7280),
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        } else if (selectedImage != null) {
                                            Text(
                                                text = selectedImage?.lastPathSegment ?: "已选择图片",
                                                color = Color(0xFF6B7280),
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Text(
                                        text = "›",
                                        fontSize = 30.sp,
                                        color = accent,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (runningCount > 0) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    text = "后台处理中：${runningCount} 个",
                                    color = Color(0xFF6B7280),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Button(
                                enabled = (runningCount > 0) || (prompt.isNotBlank() && isConfigured && !isReadingReferenceImage),
                                onClick = {
                                    if (runningCount > 0) {
                                        cancelRunningImageTasks()
                                        return@Button
                                    }

                                    ensureImageNotificationChannel(context)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }

                                    activityTaskScope.launch {
                                        val referenceBytes = selectedImageBytes

                                        if (selectedImage != null && referenceBytes == null) {
                                            status = if (isReadingReferenceImage) {
                                                "参考图仍在读取中，请稍候。"
                                            } else {
                                                "参考图缓存不可用，请重新选择图片。"
                                            }
                                            return@launch
                                        }

                                        prefs.edit {
                                            putString(ConfigKeys.BASE_URL, baseUrl.trim())
                                            putString(ConfigKeys.API_KEY, apiKey.trim())
                                            putString(ConfigKeys.API_MODE, apiMode.value)
                                            putString(ConfigKeys.GENERATE_MODEL, generateModel.trim())
                                            putString(ConfigKeys.EDIT_MODEL, editModel.trim())
                                            putString(ConfigKeys.MODEL, generateModel.trim())
                                            putString(ConfigKeys.PROMPT, prompt)
                                            putString(ConfigKeys.SIZE, size)
                                            putString(ConfigKeys.QUALITY, quality)
                                            putString(ConfigKeys.OUTPUT_FORMAT, outputFormat)
                                            putString(ConfigKeys.BACKGROUND, background)
                                            putBoolean(ConfigKeys.ONBOARDING_DONE, true)
                                        }

                                        val task = ImageTask(
                                            id = UUID.randomUUID().toString(),
                                            time = now(),
                                            mode = if (referenceBytes != null) "edit" else "generate",
                                            model = if (referenceBytes != null) editModel else generateModel,
                                            prompt = prompt,
                                            baseUrl = baseUrl.trim(),
                                            apiKey = apiKey.trim(),
                                            apiMode = apiMode,
                                            imageBytes = referenceBytes,
                                            size = size,
                                            quality = quality,
                                            outputFormat = outputFormat,
                                            background = background
                                        )
                                        startBackgroundTask(task)
                                        historyNotice = "已提交后台生成任务，结果会保留在当前页面预览；图片记录仅作为归档入口。"

                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text(
                                    when {
                                        isReadingReferenceImage -> "读取参考图..."
                                        runningCount > 0 -> "取消生成图像"
                                        else -> "生成图像"
                                    }
                                )
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                shape = RoundedCornerShape(20.dp),
                                tonalElevation = 1.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    SectionTitle("接口与模型", "首页直接调整，修改后立即保存")
                                    AppDropdownField(
                                        title = "接口模式",
                                        selected = apiMode.label,
                                        options = ApiMode.entries.map { it.label },
                                        onSelected = { label ->
                                            ApiMode.entries.firstOrNull { it.label == label }?.let {
                                                apiMode = it
                                                prefs.edit { putString(ConfigKeys.API_MODE, it.value) }
                                                status = "接口模式已保存。"
                                            }
                                        }
                                    )
                                    Button(
                                        enabled = !isDiscoveringImageModels,
                                        onClick = {
                                            if (baseUrl.isBlank() || apiKey.isBlank()) {
                                                status = "请先填写 Base URL 和 API Key。"
                                                return@Button
                                            }
                                            isDiscoveringImageModels = true
                                            status = "正在自动寻找生图模型..."
                                            activityTaskScope.launch {
                                                val result = withContext(Dispatchers.IO) {
                                                    runCatching { discoverImageModels(baseUrl.trim(), apiKey.trim()) }
                                                }
                                                isDiscoveringImageModels = false
                                                result.onSuccess { models ->
                                                    handleDiscoveredImageModels(models)
                                                }.onFailure { error ->
                                                    status = "自动寻找模型失败：${friendlyShortErrorMessage(error)}"
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(if (isDiscoveringImageModels) "正在寻找..." else "自动寻找生图模型")
                                    }
                                    AppEditableDropdownField(
                                        title = if (selectedImage != null) "图生图模型 ID" else "文生图模型 ID",
                                        value = if (selectedImage != null) customEditModel else customGenerateModel,
                                        options = modelOptions,
                                        placeholder = "输入或选择模型 ID",
                                        onValueChange = { value ->
                                            if (selectedImage != null) {
                                                customEditModel = value
                                                editModel = value
                                                prefs.edit { putString(ConfigKeys.EDIT_MODEL, value.trim()) }
                                            } else {
                                                customGenerateModel = value
                                                generateModel = value
                                                prefs.edit {
                                                    putString(ConfigKeys.GENERATE_MODEL, value.trim())
                                                    putString(ConfigKeys.MODEL, value.trim())
                                                }
                                            }
                                        },
                                        onSelected = { value ->
                                            if (selectedImage != null) {
                                                customEditModel = value
                                                editModel = value
                                                prefs.edit { putString(ConfigKeys.EDIT_MODEL, value.trim()) }
                                            } else {
                                                customGenerateModel = value
                                                generateModel = value
                                                prefs.edit {
                                                    putString(ConfigKeys.GENERATE_MODEL, value.trim())
                                                    putString(ConfigKeys.MODEL, value.trim())
                                                }
                                            }
                                            status = "模型已保存。"
                                        }
                                    )
                                }
                            }

                            ConfigEntryCard(
                                title = "生成参数",
                                primary = selectedSizeOption.title + " · " + selectedSizeOption.value,
                                secondary = "画质 $quality · $outputFormat",
                                onClick = { showParamsSheet = true }
                            )
                        }
                    }
                }

                imageBytes?.let { _ ->
                    item {
                        val currentPreviewImages = if (previewImages.isNotEmpty()) previewImages else listOfNotNull(imageBytes)
                        val safePreviewIndex = selectedPreviewIndex.coerceIn(0, currentPreviewImages.lastIndex)
                        val bytes = currentPreviewImages[safePreviewIndex]
                        val bitmap = remember(bytes) {
                            decodePreviewBitmap(bytes)
                        }
                        if (bitmap != null) {
                            ElevatedCard(
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    SectionTitle("结果预览", "图片、提示词、保存、分享和关闭都集中在这里")
                                    StatusCard("保存路径：$saveDirectoryLabel")
                                    if (previewPrompt.isNotBlank()) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .combinedClickable(
                                                    onClick = {},
                                                    onLongClick = {
                                                        copyTextToClipboard(context, "ImageForge Prompt", previewPrompt, "描述词已复制")
                                                        status = "描述词已复制。"
                                                    }
                                                ),
                                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                            shape = RoundedCornerShape(16.dp),
                                            tonalElevation = 0.dp
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .padding(14.dp)
                                                    .heightIn(max = 220.dp)
                                                    .verticalScroll(rememberScrollState()),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text("提示词", fontWeight = FontWeight.Bold)
                                                Text(
                                                    text = previewPrompt,
                                                    color = Color(0xFF4B5563),
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color.White)
                                            .border(1.dp, Color(0xFFD9E1F5), RoundedCornerShape(20.dp))
                                            .padding(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 520.dp)
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                                            )
                                        }
                                    }
                                    AutoDismissStatusCard("长图已限制预览高度，可在图片区域内上下滑动；下方描述词和按钮可继续查看。")
                                    if (currentPreviewImages.size > 1) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            currentPreviewImages.forEachIndexed { index, _ ->
                                                val selected = index == safePreviewIndex
                                                Surface(
                                                    modifier = Modifier
                                                        .padding(horizontal = 4.dp)
                                                        .size(34.dp)
                                                        .clickable {
                                                            selectedPreviewIndex = index
                                                            imageBytes = currentPreviewImages[index]
                                                            status = "正在预览第 ${index + 1}/${currentPreviewImages.size} 张。"
                                                        },
                                                    shape = CircleShape,
                                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = (index + 1).toString(),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                runCatching {
                                                    saveToGallery(context, bytes, outputFormat, customSaveDirectoryUri)
                                                }.onSuccess { saved ->
                                                    previewSavedPath = saved
                                                    status = "已保存到相册：$saved"
                                                }.onFailure {
                                                    status = "保存失败：${friendlyShortErrorMessage(it)}"
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("保存")
                                        }
                                        TextButton(
                                            onClick = {
                                                status = if (shareImageBytes(context, bytes, outputFormat)) {
                                                    "已打开系统分享。"
                                                } else {
                                                    "分享失败，请检查图片文件权限。"
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("分享")
                                        }
                                        TextButton(
                                            onClick = {
                                                imageBytes = null
                                                previewImages = emptyList()
                                                selectedPreviewIndex = 0
                                                previewPrompt = ""
                                                previewSavedPath = ""
                                                status = "已关闭结果预览。"
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("关闭")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            }
            }
        }

        ScreenRoute.HISTORY -> Scaffold(
            containerColor = pageBg,
            bottomBar = {
                BottomNavigationBar(
                    currentRoute = currentRoute,
                    onRouteSelected = { currentRoute = it }
                )
            }
        ) { padding ->
            CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(pageBg)
                    .padding(padding)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("图片记录", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text("最近生成与编辑的图片", color = Color(0xFF6B7280))
                        }

                        if (selectedHistoryKeys.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusPill(
                                    text = "已选 ${selectedHistoryKeys.size}",
                                    bg = Color(0xFFEFF6FF),
                                    fg = Color(0xFF315AA6)
                                )
                                TextButton(
                                    onClick = {
                                        selectedHistoryKeys.clear()
                                        selectedHistoryKeys.addAll(history.map { "${it.time}|${it.prompt}" })
                                    },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color(0xFFE0E7FF))
                                        .padding(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text("全选", color = Color(0xFF3730A3), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                item {
                    HistoryStatsCard(
                        successCount = history.count { it.state == "success" },
                        failedCount = history.count { it.state == "failed" },
                        runningCount = history.count { it.state == "running" }
                    )
                }
                if (history.any { it.state == "running" }) {
                    item {
                        TextButton(
                            onClick = { cancelAllHistoryRunningItems() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("取消全部处理中任务", color = Color(0xFFE11D48), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (historyNotice.isNotBlank()) {
                    item {
                        StatusCard(historyNotice)
                    }
                }

                if (history.isEmpty()) {
                    item {
                        EmptyHistoryCard()
                    }
                } else {
                    items(history.take(30)) { item ->
                        val itemKey = "${item.time}|${item.prompt}"
                        HistoryCard(
                            item = item,
                            selectionMode = selectedHistoryKeys.isNotEmpty(),
                            selected = itemKey in selectedHistoryKeys,
                            onToggleSelected = {
                                if (itemKey in selectedHistoryKeys) {
                                    selectedHistoryKeys.remove(itemKey)
                                } else {
                                    selectedHistoryKeys.add(itemKey)
                                }
                            },
                            onLongPress = {
                                if (itemKey !in selectedHistoryKeys) {
                                    selectedHistoryKeys.add(itemKey)
                                }
                            },
                            onDelete = {
                                val removedPrivateImage = deleteAppPrivateImageFromHistory(context, item.path)
                                history = history.filterNot { it.time == item.time && it.prompt == item.prompt }
                                saveHistory(prefs, history)
                                selectedHistoryKeys.remove(itemKey)
                                historyNotice = if (removedPrivateImage) {
                                    "已删除该条记录和应用内图片副本。"
                                } else {
                                    "已删除该条图片记录。"
                                }
                            },
                            onCopyError = {
                                copyTextToClipboard(context, "ImageForge Error", item.error)
                                historyNotice = "错误详情已复制。"
                            },
                            onPreview = { previewHistoryItem = item },
                            onOpen = {
                                if (item.state == "success" && item.path.startsWith("content://")) {
                                    historyNotice = if (openImageFromHistory(context, item.path)) "已打开图片。" else "图片打开失败。"
                                }
                            },
                            onSave = {
                                if (item.state == "success" && item.path.startsWith("content://")) {
                                    runCatching {
                                        saveExistingImageToGallery(context, item.path, customSaveDirectoryUri)
                                    }.onSuccess {
                                        historyNotice = "已保存到相册：$it"
                                    }.onFailure {
                                        historyNotice = "保存失败：${friendlyShortErrorMessage(it)}"
                                    }
                                }
                            },
                            onShare = {
                                if (item.state == "success" && item.path.startsWith("content://")) {
                                    historyNotice = if (shareImageFromHistory(context, item.path)) "已打开系统分享。" else "分享失败。"
                                }
                            },
                            onCancelRunning = {
                                cancelHistoryRunningItem(item)
                            }
                        )
                    }
                }
                }

                // Floating bottom-right delete FAB (only in selection mode)
                if (selectedHistoryKeys.isNotEmpty()) {
                    Surface(
                        onClick = {
                            val keys = selectedHistoryKeys.toSet()
                            val removedItems = history.filter { "${it.time}|${it.prompt}" in keys }
                            val removedPrivateImageCount = removedItems.count {
                                deleteAppPrivateImageFromHistory(context, it.path)
                            }
                            history = history.filterNot { "${it.time}|${it.prompt}" in keys }
                            saveHistory(prefs, history)
                            selectedHistoryKeys.clear()
                            historyNotice = if (removedPrivateImageCount > 0) {
                                "已删除选中的图片记录，并清理 $removedPrivateImageCount 张应用内图片副本。"
                            } else {
                                "已删除选中的图片记录。"
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 24.dp, end = 20.dp)
                            .size(60.dp),
                        shape = CircleShape,
                        color = Color(0xFFE11D48),
                        shadowElevation = 8.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除选中记录",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
            }
        }
    }
}
