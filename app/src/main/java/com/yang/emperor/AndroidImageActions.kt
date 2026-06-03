package com.yang.emperor

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.core.net.toUri
import java.io.IOException
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import java.io.File
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

private const val IMAGE_NOTIFICATION_CHANNEL_ID = "image_generation_result"
private const val IMAGE_NOTIFICATION_ID_BASE = 1001
private const val IMAGE_SAVE_DIRECTORY = "ImageForge"

fun ensureImageNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val channel = NotificationChannel(
        IMAGE_NOTIFICATION_CHANNEL_ID,
        "图片生成结果",
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = "图片生成完成后的提示"
    }

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(channel)
}

@SuppressLint("MissingPermission")
fun notifyImageReady(context: Context, imageUri: String) {
    if (!canPostNotifications(context)) return

    val openIntent = buildOpenImageNotificationIntent(context, imageUri)
    val pendingIntent = PendingIntent.getActivity(
        context,
        IMAGE_NOTIFICATION_ID_BASE,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, IMAGE_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_gallery)
        .setContentTitle("生成完成")
        .setContentText("点击查看图片")
        .setStyle(NotificationCompat.BigTextStyle().bigText("图片已保存到应用记录，点击回到 ImageForge 查看。"))
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    NotificationManagerCompat.from(context).notify(
        (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
        notification
    )
}

fun shareImageFromHistory(context: Context, imageUri: String): Boolean {
    if (!imageUri.startsWith("content://")) {
        Toast.makeText(context, "没有可分享的图片文件。", Toast.LENGTH_SHORT).show()
        return false
    }

    val uri = imageUri.toUri()
    return try {
        val mimeType = context.contentResolver.getType(uri) ?: "image/png"
        val format = when {
            mimeType.contains("jpeg", ignoreCase = true) || mimeType.contains("jpg", ignoreCase = true) -> "jpg"
            mimeType.contains("webp", ignoreCase = true) -> "webp"
            else -> "png"
        }
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取图片文件")
        shareImageBytes(context, bytes, format)
    } catch (e: Exception) {
        Toast.makeText(context, "分享失败：${e.message ?: "图片无法读取"}", Toast.LENGTH_LONG).show()
        false
    }
}

fun shareImageBytes(context: Context, bytes: ByteArray, format: String): Boolean {
    return try {
        require(bytes.isNotEmpty()) { "图片数据为空，无法分享" }
        val normalizedFormat = normalizeImageFormat(format)
        val shareDir = File(context.cacheDir, "shared_images").apply {
            if (!exists()) mkdirs()
        }

        shareDir.listFiles()?.forEach { oldFile ->
            if (oldFile.isFile) runCatching { oldFile.delete() }
        }

        val shareFile = File(
            shareDir,
            "imageforge_share_${System.currentTimeMillis()}.${normalizedFormat.extension}"
        )
        shareFile.outputStream().use { output ->
            output.write(bytes)
            output.flush()
        }

        val shareUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            shareFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = normalizedFormat.mimeType
            putExtra(Intent.EXTRA_STREAM, shareUri)
            clipData = ClipData.newUri(context.contentResolver, "imageforge_shared_image", shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "分享图片").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
        true
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "没有可用的分享应用。", Toast.LENGTH_SHORT).show()
        false
    } catch (e: Exception) {
        Toast.makeText(context, "分享失败：${e.message ?: "图片无法读取"}", Toast.LENGTH_LONG).show()
        false
    }
}

fun openImageFromHistory(context: Context, imageUri: String): Boolean {
    if (!imageUri.startsWith("content://")) {
        Toast.makeText(context, "没有可打开的图片文件。", Toast.LENGTH_SHORT).show()
        return false
    }

    return try {
        context.startActivity(buildOpenImageIntent(context, imageUri))
        true
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "没有可用的图片查看应用。", Toast.LENGTH_SHORT).show()
        false
    } catch (e: Exception) {
        Toast.makeText(context, "打开失败：${e.message ?: "图片无法打开"}", Toast.LENGTH_LONG).show()
        false
    }
}

fun cleanupReferenceImageCache(context: Context, maxAgeMillis: Long = 6 * 60 * 60 * 1000L) {
    val dir = File(context.cacheDir, "reference_images")
    val now = System.currentTimeMillis()
    dir.listFiles()?.forEach { file ->
        if (file.isFile && (maxAgeMillis <= 0L || now - file.lastModified() > maxAgeMillis)) {
            runCatching { file.delete() }
        }
    }
}

fun clearReferenceImageCache(context: Context) {
    cleanupReferenceImageCache(context, maxAgeMillis = 0L)
}

fun cacheReferenceImageBytes(context: Context, uri: Uri): ByteArray {
    cleanupReferenceImageCache(context)

    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        input.readBytes()
    } ?: throw IOException("无法读取参考图文件，请重新选择图片")

    require(bytes.isNotEmpty()) { "参考图文件为空，请重新选择图片" }

    val cacheDir = File(context.cacheDir, "reference_images").apply {
        if (!exists()) mkdirs()
    }

    cacheDir.listFiles()?.forEach { oldFile ->
        if (oldFile.isFile) runCatching { oldFile.delete() }
    }

    val cacheFile = File(cacheDir, "reference_${System.currentTimeMillis()}.img")
    try {
        cacheFile.outputStream().use { output ->
            output.write(bytes)
            output.flush()
        }
    } catch (e: Exception) {
        runCatching { cacheFile.delete() }
        throw IOException("参考图缓存写入失败，请重新选择图片", e)
    }

    return cacheFile.inputStream().use { it.readBytes() }
}

fun saveExistingImageToGallery(
    context: Context,
    imageUri: String,
    customDirectoryUri: Uri? = null
): String {
    require(imageUri.startsWith("content://")) { "没有可保存的图片文件" }
    val uri = imageUri.toUri()
    val mimeType = context.contentResolver.getType(uri).orEmpty()
    val format = when {
        mimeType.contains("jpeg", ignoreCase = true) || mimeType.contains("jpg", ignoreCase = true) -> "jpg"
        mimeType.contains("webp", ignoreCase = true) -> "webp"
        else -> "png"
    }
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("无法读取图片文件")
    return saveToGallery(context, bytes, format, customDirectoryUri)
}

fun saveImageToAppFiles(
    context: Context,
    bytes: ByteArray,
    format: String
): String {
    require(bytes.isNotEmpty()) { "图片数据为空，无法写入应用记录" }
    require(BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null) {
        "接口返回的数据不是可解析图片，已拒绝写入成功记录"
    }

    val normalizedFormat = normalizeImageFormat(format)
    val generatedDir = File(context.filesDir, "generated_images").apply {
        if (!exists() && !mkdirs()) {
            throw IOException("无法创建应用内部图片目录")
        }
    }
    val imageFile = File(
        generatedDir,
        "imageforge_${System.currentTimeMillis()}.${normalizedFormat.extension}"
    )

    try {
        imageFile.outputStream().use { output ->
            output.write(bytes)
            output.flush()
        }
        if (!imageFile.isFile || imageFile.length() <= 0L) {
            throw IOException("图片文件写入后为空")
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
        val readable = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input) != null
        } == true
        if (!readable) {
            throw IOException("图片已写入但无法通过应用 URI 读取")
        }
        return uri.toString()
    } catch (e: Exception) {
        runCatching { imageFile.delete() }
        throw IOException("无法写入可读取的应用内部图片记录", e)
    }
}

fun deleteAppPrivateImageFromHistory(context: Context, imageUri: String): Boolean {
    if (!imageUri.startsWith("content://")) return false

    val uri = imageUri.toUri()
    val expectedAuthority = "${context.packageName}.fileprovider"
    if (uri.authority != expectedAuthority) return false

    val decodedPath = Uri.decode(uri.path.orEmpty())
    val encodedPath = uri.encodedPath.orEmpty()
    val isGeneratedImage = decodedPath.contains("/generated_images/") ||
        encodedPath.contains("generated_images")

    if (!isGeneratedImage) return false

    return runCatching {
        context.contentResolver.delete(uri, null, null) > 0
    }.getOrDefault(false)
}

fun saveToGallery(
    context: Context,
    bytes: ByteArray,
    format: String,
    customDirectoryUri: Uri? = null
): String {
    require(bytes.isNotEmpty()) { "图片数据为空，无法保存" }

    val normalizedFormat = normalizeImageFormat(format)
    val name = "image_${System.currentTimeMillis()}.${normalizedFormat.extension}"

    if (customDirectoryUri != null) {
        return saveToCustomDirectory(
            context = context,
            bytes = bytes,
            name = name,
            mimeType = normalizedFormat.mimeType,
            directoryUri = customDirectoryUri
        )
    }

    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, normalizedFormat.mimeType)
        put(
            MediaStore.Images.Media.RELATIVE_PATH,
            Environment.DIRECTORY_PICTURES + "/$IMAGE_SAVE_DIRECTORY"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("无法创建图片文件")

    try {
        resolver.openOutputStream(uri)?.use { output ->
            output.write(bytes)
            output.flush()
        } ?: error("无法打开图片输出流")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val publishValues = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(uri, publishValues, null, null)
        }

        return uri.toString()
    } catch (e: Exception) {
        runCatching { resolver.delete(uri, null, null) }
        throw e
    }
}

private fun saveToCustomDirectory(
    context: Context,
    bytes: ByteArray,
    name: String,
    mimeType: String,
    directoryUri: Uri
): String {
    val resolver = context.contentResolver

    var createdImageUri: Uri? = null
    try {
        val targetDirectoryUri = if (DocumentsContract.isTreeUri(directoryUri)) {
            val treeDocumentId = DocumentsContract.getTreeDocumentId(directoryUri)
            DocumentsContract.buildDocumentUriUsingTree(directoryUri, treeDocumentId)
        } else {
            directoryUri
        }

        val imageUri = DocumentsContract.createDocument(
            resolver,
            targetDirectoryUri,
            mimeType,
            name
        ) ?: throw IOException("无法在自定义保存目录中创建图片文件：$directoryUri")
        createdImageUri = imageUri

        resolver.openOutputStream(imageUri, "w")?.use { output ->
            output.write(bytes)
            output.flush()
        } ?: throw IOException("无法打开自定义保存目录的图片输出流：$imageUri")

        return imageUri.toString()
    } catch (e: SecurityException) {
        createdImageUri?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
        throw IOException("没有自定义保存目录的写入权限，请重新选择保存目录并授权：$directoryUri", e)
    } catch (e: IllegalArgumentException) {
        createdImageUri?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
        throw IOException("自定义保存目录 URI 无效：$directoryUri。请重新选择保存目录。", e)
    } catch (e: IOException) {
        createdImageUri?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
        throw e
    } catch (e: Exception) {
        createdImageUri?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
        throw IOException("保存到自定义目录失败：$directoryUri", e)
    }
}

private fun canPostNotifications(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
}

private fun buildOpenImageNotificationIntent(context: Context, imageUri: String): Intent {
    return Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra("open_generated_image", true)
        putExtra("image_uri", imageUri)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

private fun buildOpenImageIntent(context: Context, imageUri: String): Intent {
    if (imageUri.startsWith("content://")) {
        val uri = imageUri.toUri()
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/*")
            clipData = ClipData.newUri(context.contentResolver, "generated_image", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    return Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

private data class ImageFormat(
    val extension: String,
    val mimeType: String
)

private fun normalizeImageFormat(format: String): ImageFormat {
    return when (format.lowercase()) {
        "jpg", "jpeg" -> ImageFormat(extension = "jpg", mimeType = "image/jpeg")
        "webp" -> ImageFormat(extension = "webp", mimeType = "image/webp")
        else -> ImageFormat(extension = "png", mimeType = "image/png")
    }
}
