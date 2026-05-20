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
import androidx.compose.ui.text.input.VisualTransformation
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import com.yang.emperor.ui.theme.AppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.util.UUID
import java.net.URL
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.io.IOException

private const val DEVELOPER_QQ = "2753761311"
private const val DEVELOPER_QQ_AVATAR_URL = "https://q1.qlogo.cn/g?b=qq&nk=$DEVELOPER_QQ&s=640"
private const val IMAGEFORGE_REPO_URL = "https://github.com/Wzindx/ImageForge"


private var developerAvatarCache: Bitmap? = null

private suspend fun loadDeveloperAvatarBitmap(): Bitmap? {
    developerAvatarCache?.let { return it }
    return withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(DEVELOPER_QQ_AVATAR_URL).openConnection()
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 ImageForge")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.getInputStream().use { input ->
                BitmapFactory.decodeStream(input)
            }
        }.getOrNull()?.also { developerAvatarCache = it }
    }
}

private fun openDeveloperQQ(context: Context) {
    val qqCardIntent = Intent(
        Intent.ACTION_VIEW,
        "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$DEVELOPER_QQ&card_type=person&source=qrcode".toUri()
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val qqCardFallbackIntent = Intent(
        Intent.ACTION_VIEW,
        "mqq://card/show_pslcard?src_type=internal&version=1&uin=$DEVELOPER_QQ&card_type=person&source=qrcode".toUri()
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val webIntent = Intent(
        Intent.ACTION_VIEW,
        "https://qm.qq.com/cgi-bin/qm/qr?k=&jump_from=webapi&authKey=&uin=$DEVELOPER_QQ".toUri()
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    runCatching {
        context.startActivity(qqCardIntent)
    }.getOrElse {
        runCatching {
            context.startActivity(qqCardFallbackIntent)
        }.getOrElse {
            runCatching { context.startActivity(webIntent) }
        }
    }
}

private fun openImageForgeRepository(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, IMAGEFORGE_REPO_URL.toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun SettingsScreen(
    baseUrl: String,
    apiKey: String,
    apiMode: ApiMode,
    customGenerateModel: String,
    currentGenerateModel: String,
    customEditModel: String,
    currentEditModel: String,
    recommendedModels: List<String>,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onApiModeChange: (ApiMode) -> Unit,
    onCustomGenerateModelChange: (String) -> Unit,
    onSelectGenerateModel: (String) -> Unit,
    onCustomEditModelChange: (String) -> Unit,
    onSelectEditModel: (String) -> Unit,
    saveDirectoryLabel: String,
    onChooseSaveDirectory: () -> Unit,
    settingsNotice: String,
    onBack: () -> Unit,
    onClearConfig: () -> Unit,
    onShowOnboarding: () -> Unit,
    onSave: () -> Unit,
    outerPadding: PaddingValues = PaddingValues()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isApiKeyFocused by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = pageBg
    ) { padding ->
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(outerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 18.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = cardBg),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SectionTitle("连接配置", "这里只保留接口地址和密钥；模型与生成参数在首页二级栏调整")

                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = onBaseUrlChange,
                        label = { Text("Base URL") },
                        placeholder = { Text("例如：https://你的服务地址/v1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = onApiKeyChange,
                        label = { Text("API Key") },
                        placeholder = { Text("输入你的密钥") },
                        visualTransformation = if (isApiKeyFocused) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isApiKeyFocused = it.isFocused },
                        shape = RoundedCornerShape(18.dp)
                    )


                    Button(
                        onClick = onSave,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("保存连接设置")
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onChooseSaveDirectory() },
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = RoundedCornerShape(18.dp),
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "图片保存路径",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = saveDirectoryLabel,
                                    color = Color(0xFF6B7280),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "点击选择系统文件管理器目录",
                                    color = accent,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Text(
                                text = "›",
                                fontSize = 28.sp,
                                color = accent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    TextButton(
                        onClick = onClearConfig,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("清除连接配置信息")
                    }

                    if (settingsNotice.isNotBlank()) {
                        StatusCard(settingsNotice)
                    }

                    HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.55f))

                    SectionTitle("软件信息", "版本、作者和开源仓库")

                    val developerAvatar by produceState<Bitmap?>(initialValue = null) {
                        value = loadDeveloperAvatarBitmap()
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { openDeveloperQQ(context) },
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = RoundedCornerShape(18.dp),
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(accent.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (developerAvatar != null) {
                                        Image(
                                            bitmap = developerAvatar!!.asImageBitmap(),
                                            contentDescription = "开发者 QQ 头像",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Text(
                                            text = "QQ",
                                            color = accent,
                                            fontWeight = FontWeight.Black,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = "开发者 QQ",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = DEVELOPER_QQ,
                                        color = Color(0xFF6B7280),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Text(
                                text = "›",
                                fontSize = 28.sp,
                                color = accent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = RoundedCornerShape(18.dp),
                        tonalElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "应用名称",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "ImageForge / 通用图像工坊",
                                color = Color(0xFF6B7280),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "版本：${BuildConfig.VERSION_NAME}",
                                color = Color(0xFF6B7280),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { openImageForgeRepository(context) },
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                shape = RoundedCornerShape(14.dp),
                                tonalElevation = 0.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "开源仓库",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = IMAGEFORGE_REPO_URL,
                                            color = Color(0xFF6B7280),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "›",
                                        fontSize = 24.sp,
                                        color = accent,
                                        fontWeight = FontWeight.SemiBold
                                    )
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