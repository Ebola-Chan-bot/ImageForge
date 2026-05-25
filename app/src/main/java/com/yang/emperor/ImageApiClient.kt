package com.yang.emperor

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.EOFException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.util.UUID
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

private const val CONNECT_TIMEOUT_MS = 30_000
private const val READ_TIMEOUT_MS = 180_000
private const val MAX_ERROR_BODY_CHARS = 20_000
private const val MAX_NETWORK_ATTEMPTS = 3
private const val RETRY_DELAY_MS = 800L

private val activeImageConnections = ConcurrentHashMap<String, MutableSet<HttpURLConnection>>()

fun cancelImageRequest(requestId: String) {
    activeImageConnections.remove(requestId)?.forEach { conn ->
        runCatching { conn.disconnect() }
    }
}

private fun trackConnection(requestId: String?, conn: HttpURLConnection): HttpURLConnection {
    if (!requestId.isNullOrBlank()) {
        val connections = activeImageConnections.getOrPut(requestId) {
            Collections.newSetFromMap(ConcurrentHashMap<HttpURLConnection, Boolean>())
        }
        connections.add(conn)
    }
    return conn
}

private fun closeConnection(requestId: String?, conn: HttpURLConnection) {
    runCatching { conn.disconnect() }
    if (!requestId.isNullOrBlank()) {
        activeImageConnections[requestId]?.remove(conn)
        if (activeImageConnections[requestId]?.isEmpty() == true) {
            activeImageConnections.remove(requestId)
        }
    }
}

fun endpoint(baseUrl: String, path: String): String {
    val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
    require(normalizedBaseUrl.isNotBlank()) { "请填写 Base URL" }
    require(
        normalizedBaseUrl.startsWith("https://") || normalizedBaseUrl.startsWith("http://")
    ) { "Base URL 必须以 http:// 或 https:// 开头" }

    val normalizedPath = if (path.startsWith("/")) path else "/$path"
    return if (normalizedBaseUrl.endsWith("/v1")) {
        "$normalizedBaseUrl$normalizedPath"
    } else {
        "$normalizedBaseUrl/v1$normalizedPath"
    }
}

fun callGenerate(
    baseUrl: String,
    apiKey: String,
    model: String,
    prompt: String,
    n: Int,
    size: String,
    quality: String,
    requestId: String? = null
): ByteArray = callGenerateImages(
    baseUrl = baseUrl,
    apiKey = apiKey,
    model = model,
    prompt = prompt,
    n = n,
    size = size,
    quality = quality,
    requestId = requestId
).first()

fun callGenerateImages(
    baseUrl: String,
    apiKey: String,
    model: String,
    prompt: String,
    n: Int,
    size: String,
    quality: String,
    requestId: String? = null
): List<ByteArray> {
    require(apiKey.isNotBlank()) { "请填写 API Key" }
    require(model.isNotBlank()) { "请填写模型 ID" }
    require(prompt.isNotBlank()) { "请填写 Prompt" }

    val requestedCount = n.coerceIn(1, 10)
    val body = JSONObject()
        .put("model", model.trim())
        .put("prompt", prompt)
        .put("n", requestedCount)
        .put("size", size)
        .put("quality", quality)

    return withNetworkRetries("文生图请求") {
        val conn = openPostConnection(endpoint(baseUrl, "/images/generations"), apiKey, requestId = requestId)
        try {
            parseImageResponsesAfterWrite(conn, body)
        } finally {
            closeConnection(requestId, conn)
        }
    }
}

fun callEdit(
    baseUrl: String,
    apiKey: String,
    model: String,
    prompt: String,
    imageBytes: ByteArray?,
    size: String,
    quality: String,
    outputFormat: String,
    background: String,
    requestId: String? = null
): ByteArray {
    require(apiKey.isNotBlank()) { "请填写 API Key" }
    require(model.isNotBlank()) { "请填写模型 ID" }
    require(prompt.isNotBlank()) { "请填写编辑指令" }
    val sourceImageBytes = requireNotNull(imageBytes) { "无法读取参考图，请重新选择图片后再试" }

    return withNetworkRetries("图生图请求") {
        val boundary = "----AndroidBoundary${UUID.randomUUID()}"
        val conn = openPostConnection(
            url = endpoint(baseUrl, "/images/edits"),
            apiKey = apiKey,
            contentType = "multipart/form-data; boundary=$boundary",
            requestId = requestId
        )

        try {
            conn.outputStream.use { out ->
                fun writeText(value: String) {
                    out.write(value.toByteArray(Charsets.UTF_8))
                }

                fun field(name: String, value: String) {
                    writeText("--$boundary\r\n")
                    writeText("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                    writeText("$value\r\n")
                }

                field("model", model.trim())
                field("prompt", prompt)
                field("size", size)
                field("quality", quality)
                field("output_format", outputFormat)
                if (background.isNotBlank()) field("background", background)

                writeText("--$boundary\r\n")
                writeText("Content-Disposition: form-data; name=\"image\"; filename=\"image.png\"\r\n")
                writeText("Content-Type: image/png\r\n\r\n")
                out.write(sourceImageBytes)
                writeText("\r\n--$boundary--\r\n")
            }
            parseImageResponse(conn)
        } finally {
            closeConnection(requestId, conn)
        }
    }
}

fun callEditGenerationsCompat(
    baseUrl: String,
    apiKey: String,
    model: String,
    prompt: String,
    imageBytes: ByteArray?,
    size: String,
    quality: String,
    requestId: String? = null
): ByteArray {
    require(apiKey.isNotBlank()) { "请填写 API Key" }
    require(model.isNotBlank()) { "请填写模型 ID" }
    require(prompt.isNotBlank()) { "请填写编辑指令" }
    val sourceImageBytes = requireNotNull(imageBytes) { "无法读取参考图，请重新选择图片后再试" }

    val inputImageDataUrl = buildCompactImageDataUrl(sourceImageBytes)
    val compatPrompt = """
        $prompt

        Reference image is provided in the request image fields. Use it as the visual reference for this edit.
    """.trimIndent()

    val body = JSONObject()
        .put("model", model.trim())
        .put("prompt", compatPrompt)
        .put("n", 1)
        .put("size", size)
        .put("quality", quality)
        .put("image", inputImageDataUrl)
        .put("reference_image", inputImageDataUrl)

    return withNetworkRetries("兼容模式图生图请求") {
        val conn = openPostConnection(endpoint(baseUrl, "/images/generations"), apiKey, requestId = requestId)
        try {
            writeJsonBody(conn, body)
            parseImageResponse(conn)
        } finally {
            closeConnection(requestId, conn)
        }
    }
}

fun callEditResponses(
    baseUrl: String,
    apiKey: String,
    model: String,
    prompt: String,
    imageBytes: ByteArray?,
    size: String,
    quality: String,
    outputFormat: String,
    background: String,
    requestId: String? = null
): ByteArray {
    require(apiKey.isNotBlank()) { "请填写 API Key" }
    require(model.isNotBlank()) { "请填写模型 ID" }
    require(prompt.isNotBlank()) { "请填写编辑指令" }
    val sourceImageBytes = requireNotNull(imageBytes) { "无法读取参考图，请重新选择图片后再试" }

    val inputImageDataUrl = "data:image/png;base64," + Base64.encodeToString(sourceImageBytes, Base64.NO_WRAP)
    val inputContent = JSONArray().apply {
        put(
            JSONObject()
                .put("type", "input_text")
                .put("text", "Use the following text as the complete prompt. Do not rewrite it:\n$prompt")
        )
        put(JSONObject().put("type", "input_image").put("image_url", inputImageDataUrl))
    }

    val tool = JSONObject()
        .put("type", "image_generation")
        .put("action", "edit")
        .put("size", size)
        .put("quality", quality)
        .put("output_format", outputFormat)

    if (background.isNotBlank()) {
        tool.put("background", background)
    }

    val body = JSONObject()
        .put("model", model.trim())
        .put(
            "input",
            JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("content", inputContent)
            )
        )
        .put("tools", JSONArray().put(tool))
        .put("tool_choice", "required")

    return withNetworkRetries("Responses 图像请求") {
        val conn = openPostConnection(endpoint(baseUrl, "/responses"), apiKey, requestId = requestId)
        try {
            writeJsonBody(conn, body)
            parseResponsesImageResponse(conn)
        } finally {
            closeConnection(requestId, conn)
        }
    }
}

private fun parseImageResponsesAfterWrite(conn: HttpURLConnection, body: JSONObject): List<ByteArray> {
    writeJsonBody(conn, body)
    return parseImageResponses(conn)
}

fun parseImageResponse(conn: HttpURLConnection): ByteArray = parseImageResponses(conn).first()

fun parseImageResponses(conn: HttpURLConnection): List<ByteArray> {
    val code = readResponseCode(conn)
    val text = readResponseTextSafely(conn, code)
    if (code !in 200..299) error("HTTP $code: ${text.truncateForError()}")

    val data = JSONObject(text).optJSONArray("data") ?: error("响应缺少 data")
    if (data.length() == 0) error("响应 data 为空")

    val images = mutableListOf<ByteArray>()
    val itemErrors = mutableListOf<String>()
    for (index in 0 until data.length()) {
        val item = data.optJSONObject(index)
        if (item == null) {
            itemErrors.add("第 ${index + 1} 项不是有效图片对象")
            continue
        }

        val b64 = item.optString("b64_json", "")
        if (b64.isNotBlank()) {
            images.add(decodeBase64Image(b64))
            continue
        }

        val url = item.optString("url", "")
        if (url.isNotBlank()) {
            images.add(download(url))
            continue
        }

        itemErrors.add("第 ${index + 1} 项既没有 url 也没有 b64_json")
    }

    if (images.isNotEmpty()) return images
    error(itemErrors.firstOrNull() ?: "响应中没有可用图片数据")
}

fun parseResponsesImageResponse(conn: HttpURLConnection): ByteArray {
    val code = readResponseCode(conn)
    val text = readResponseTextSafely(conn, code)
    if (code !in 200..299) error("HTTP $code: ${text.truncateForError()}")

    val output = JSONObject(text).optJSONArray("output") ?: error("响应缺少 output")

    for (index in 0 until output.length()) {
        val item = output.optJSONObject(index) ?: continue
        if (item.optString("type") != "image_generation_call") continue

        val result = item.optString("result", "")
        if (result.isNotBlank()) {
            return decodeBase64Image(result)
        }
    }

    for (index in 0 until output.length()) {
        val item = output.optJSONObject(index) ?: continue
        if (item.optString("type") != "image_generation_call") continue

        val resultUrl = item.optString("url", "")
        if (resultUrl.isNotBlank()) {
            return download(resultUrl)
        }

        val nested = item.optJSONObject("result")
        val nestedUrl = nested?.optString("url", "") ?: ""
        if (nestedUrl.isNotBlank()) {
            return download(nestedUrl)
        }

        val nestedB64 = nested?.optString("b64_json", "") ?: ""
        if (nestedB64.isNotBlank()) {
            return decodeBase64Image(nestedB64)
        }
    }

    error("Responses API 未返回可用图片数据（既没有 result/base64，也没有 url）")
}

fun discoverImageModels(baseUrl: String, apiKey: String): List<String> {
    require(apiKey.isNotBlank()) { "请填写 API Key" }

    return withNetworkRetries("模型列表请求") {
        val conn = openGetConnection(endpoint(baseUrl, "/models"), apiKey)
        try {
            val code = readResponseCode(conn, "模型列表接口")
            val text = readResponseTextSafely(conn, code)
            if (code !in 200..299) error("HTTP $code: ${text.truncateForError()}")

            val data = JSONObject(text).optJSONArray("data") ?: JSONArray()
            val models = mutableListOf<ModelCandidate>()
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val id = item.optString("id", "").orEmpty().trim()
                if (id.isNotBlank()) {
                    imageGenerationModelScore(item, id).takeIf { it > 0 }?.let { score ->
                        models.add(ModelCandidate(id, score))
                    }
                }
            }
            models
                .groupBy { it.id }
                .map { (_, candidates) -> candidates.maxBy { it.score } }
                .sortedWith(compareByDescending<ModelCandidate> { it.score }.thenBy { it.id })
                .map { it.id }
        } finally {
            conn.disconnect()
        }
    }
}

private data class ModelCandidate(val id: String, val score: Int)

private fun imageGenerationModelScore(item: JSONObject, id: String): Int {
    val searchable = buildString {
        append(id.lowercase())
        append(' ')
        append(item.optString("type", "").lowercase())
        append(' ')
        append(item.optString("object", "").lowercase())
        append(' ')
        append(item.optString("owned_by", "").lowercase())
        append(' ')
        append(item.toString().lowercase())
    }
    val includeScores = listOf(
        "gpt-image" to 120,
        "image_generation" to 110,
        "image-generation" to 110,
        "image generation" to 110,
        "text-to-image" to 100,
        "image" to 90,
        "imagine" to 90,
        "dall-e" to 90,
        "dalle" to 90,
        "imagen" to 90,
        "flux" to 80,
        "sdxl" to 80,
        "stable-diffusion" to 80,
        "stable_diffusion" to 80,
        "midjourney" to 80,
        "qwen-image" to 80,
        "seedream" to 80,
        "jimeng" to 80,
        "kolors" to 80,
        "ideogram" to 80,
        "recraft" to 80,
        "grok-imagine" to 80
    )
    val excludeKeywords = listOf(
        "embedding", "text-embedding", "audio", "speech", "tts", "transcribe",
        "whisper", "moderation", "rerank", "vision-preview", "realtime",
        "chat", "instruct", "asr", "ocr"
    )
    val score = includeScores.filter { searchable.contains(it.first) }.maxOfOrNull { it.second } ?: 0
    if (score == 0) return 0
    val idLower = id.lowercase()
    if (excludeKeywords.any { idLower.contains(it) } && !idLower.contains("image")) return 0
    return score
}

fun download(url: String): ByteArray {
    return withNetworkRetries("图片下载") {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.setRequestProperty("Accept", "image/*,*/*")
        conn.setRequestProperty("Connection", "close")

        try {
            val code = readResponseCode(conn, "图片下载")
            if (code !in 200..299) {
                val errorText = readResponseTextSafely(conn, code).truncateForError()
                error("图片下载失败 HTTP $code: $errorText")
            }
            runCatching {
                conn.inputStream.use { it.readBytes() }
            }.getOrElse { e ->
                throw friendlyNetworkIOException(e, "图片下载响应体读取失败")
            }
        } finally {
            conn.disconnect()
        }
    }
}

private fun <T> withNetworkRetries(operationName: String, block: () -> T): T {
    var lastError: IOException? = null

    for (attempt in 1..MAX_NETWORK_ATTEMPTS) {
        try {
            return block()
        } catch (e: IOException) {
            lastError = e
            if (attempt >= MAX_NETWORK_ATTEMPTS || !isRetryableNetworkFailure(e)) {
                if (attempt > 1) {
                    throw retryExhaustedIOException(operationName, attempt, e)
                }
                throw e
            }

            runCatching {
                Thread.sleep(RETRY_DELAY_MS * attempt)
            }
        }
    }

    throw retryExhaustedIOException(
        operationName = operationName,
        attempts = MAX_NETWORK_ATTEMPTS,
        error = lastError ?: IOException("未知网络错误")
    )
}

private fun retryExhaustedIOException(operationName: String, attempts: Int, error: IOException): IOException {
    val message = buildString {
        append(operationName)
        append("失败，已自动重试 ")
        append((attempts - 1).coerceAtLeast(0))
        append(" 次仍未成功。\n")
        append("这通常表示当前 VPN/代理节点、网关转发、Base URL 中转服务或目标接口在读取响应时不稳定。")
        append("建议切换代理节点、关闭/开启代理对比测试，或更换更稳定的 Base URL。")
        append("\n\n最后一次错误：\n")
        append(error.message.orEmpty())
    }
    return IOException(message, error)
}

private fun isRetryableNetworkFailure(error: Throwable): Boolean {
    val chain = generateSequence(error) { it.cause }.toList()
    val allMessages = chain.joinToString("\n") { cause ->
        "${cause.javaClass.name}: ${cause.message.orEmpty()}"
    }

    return chain.any { it is EOFException || it is SocketException || it is SocketTimeoutException } ||
        allMessages.contains("Software caused connection abort", ignoreCase = true) ||
        allMessages.contains("unexpected end of stream", ignoreCase = true) ||
        allMessages.contains("EOFException", ignoreCase = true) ||
        allMessages.contains("读取 HTTP 响应头失败", ignoreCase = true) ||
        allMessages.contains("connection reset", ignoreCase = true) ||
        allMessages.contains("socket closed", ignoreCase = true) ||
        allMessages.contains("broken pipe", ignoreCase = true) ||
        allMessages.contains("premature end", ignoreCase = true) ||
        allMessages.contains("stream was reset", ignoreCase = true) ||
        allMessages.contains("timeout", ignoreCase = true)
}

private fun openPostConnection(
    url: String,
    apiKey: String,
    contentType: String = "application/json",
    requestId: String? = null
): HttpURLConnection {
    return trackConnection(requestId, (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
        doOutput = true
        setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
        setRequestProperty("Content-Type", contentType)
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Connection", "close")
    })
}

private fun openGetConnection(url: String, apiKey: String): HttpURLConnection {
    return (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
        setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Connection", "close")
    }
}

private fun writeJsonBody(conn: HttpURLConnection, body: JSONObject) {
    conn.outputStream.use { out ->
        out.write(body.toString().toByteArray(Charsets.UTF_8))
    }
}

private fun readResponseCode(conn: HttpURLConnection, stage: String = "图像生成接口"): Int {
    return runCatching {
        conn.responseCode
    }.getOrElse { e ->
        throw friendlyNetworkIOException(e, "$stage 读取 HTTP 响应头失败")
    }
}

private fun readResponseTextSafely(conn: HttpURLConnection, code: Int): String {
    return runCatching {
        val stream = if (code in 200..299) {
            conn.inputStream
        } else {
            conn.errorStream ?: conn.inputStream
        }
        stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.getOrElse { e ->
        throw friendlyNetworkIOException(e, "HTTP $code 响应体读取失败")
    }
}

private fun friendlyNetworkIOException(error: Throwable, stage: String): IOException {
    val chain = generateSequence(error) { it.cause }.toList()
    val allMessages = chain.joinToString("\n") { cause ->
        "${cause.javaClass.name}: ${cause.message.orEmpty()}"
    }
    val isSoftwareAbort = allMessages.contains("Software caused connection abort", ignoreCase = true)
    val isUnexpectedEnd = allMessages.contains("unexpected end of stream", ignoreCase = true) ||
        allMessages.contains("EOFException", ignoreCase = true) ||
        chain.any { it is EOFException }

    val hint = if (isSoftwareAbort) {
        "VPN/代理连接在读取 HTTP 响应头或响应体时被系统/代理节点中断：Software caused connection abort。常见原因是当前代理节点复用连接不稳定、中转网关提前关闭 TLS 连接、Base URL 上游响应过慢或网络在大请求后抖动。App 已强制使用 Connection: close、取消时主动 disconnect，并会自动重试；如果仍失败，请优先切换 VPN 节点，或关闭/开启代理做对比测试，再考虑更换更稳定的 Base URL。"
    } else if (isUnexpectedEnd) {
        "网络连接在读取响应时提前断开。常见原因：VPN/代理节点或中转网关提前关闭连接、Base URL 服务不稳定、HTTP/1.1 长连接复用异常、接口返回空响应，或当前网络抖动。App 已使用 Connection: close 并会对这类断流自动重试；如果仍失败，请切换代理节点、关闭/开启代理对比测试，或更换更稳定的 Base URL。"
    } else {
        "网络请求失败。请检查网络、Base URL、代理节点、网关转发、服务端状态或接口兼容性。"
    }

    val message = buildString {
        append(stage)
        append("\n")
        append(hint)
        append("\n\n原始异常链：\n")
        append(allMessages.ifBlank { "${error.javaClass.name}: ${error.message.orEmpty()}" })
    }

    return IOException(message, error)
}

private fun decodeBase64Image(value: String): ByteArray {
    val pureBase64 = value
        .removePrefix("data:image/png;base64,")
        .removePrefix("data:image/jpeg;base64,")
        .removePrefix("data:image/webp;base64,")
    return Base64.decode(pureBase64, Base64.DEFAULT)
}

private fun String.truncateForError(): String {
    if (length <= MAX_ERROR_BODY_CHARS) return this
    return take(MAX_ERROR_BODY_CHARS) + "\n...（错误响应过长，已截断）"
}
