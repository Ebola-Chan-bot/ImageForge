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

@Composable
internal fun OnboardingScreen(
    baseUrl: String,
    apiKey: String,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSkip: () -> Unit,
    onSave: () -> Unit
) {
    BackHandler(enabled = true) {
        // 引导页不响应系统返回键，避免误触直接回到主页面。
    }
    var page by remember { mutableIntStateOf(0) }
    val totalPages = 4
    val canSave = baseUrl.isNotBlank() && apiKey.isNotBlank()

    Scaffold(containerColor = pageBg) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ImageForge",
                    color = Color(0xFF111827),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onSkip) {
                    Text("跳过", color = Color(0xFF6B7280), fontWeight = FontWeight.SemiBold)
                }
            }

            LinearProgressIndicator(
                progress = (page + 1).toFloat() / totalPages.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = accent,
                trackColor = Color(0xFFE4E8F5)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                ElevatedCard(
                    modifier = Modifier.fillMaxSize(),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFF8FAFF)),
                    shape = RoundedCornerShape(36.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 22.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (page) {
                            0 -> OobeIntroPage()
                            1 -> OobeFeaturePage()
                            2 -> OobeConfigPage(
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                onBaseUrlChange = onBaseUrlChange,
                                onApiKeyChange = onApiKeyChange
                            )
                            else -> OobeReadyPage(canSave = canSave)
                        }
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (page > 0) {
                    TextButton(
                        onClick = { page-- },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Text("上一步")
                    }
                }

                Button(
                    onClick = {
                        if (page < totalPages - 1) {
                            page++
                        } else if (canSave) {
                            onSave()
                        } else {
                            onSkip()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.56f),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text(
                        text = when {
                            page < totalPages - 1 -> "继续"
                            canSave -> "保存并开始"
                            else -> "进入应用"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun OobeIntroPage() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFFE8EEFF)),
            contentAlignment = Alignment.Center
        ) {
            Text("AI", color = accent, fontSize = 34.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = "欢迎使用通用图像工坊",
            color = Color(0xFF111827),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "选择接口，输入提示词，即可开始生成。",
            color = Color(0xFF6B7280),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}

@Composable
private fun OobeFeaturePage() {
    var isApiKeyFocused by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "清爽风格界面",
            color = Color(0xFF111827),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "大圆角卡片、浅色层级和柔和主色，减少干扰。",
            color = Color(0xFF6B7280),
            lineHeight = 22.sp
        )
        OobeFeatureItem("生成与编辑", "文生图、图生图集中处理。")
        OobeFeatureItem("后台任务", "生成中也可以切换页面。")
        OobeFeatureItem("历史记录", "完成后可查看、下载和分享。")
    }
}

@Composable
private fun OobeFeatureItem(title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFFF0F3FF))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text("✓", color = accent, fontWeight = FontWeight.Black)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Color(0xFF111827), fontWeight = FontWeight.Bold)
            Text(description, color = Color(0xFF6B7280), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun OobeConfigPage(
    baseUrl: String,
    apiKey: String,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit
) {
    var isApiKeyFocused by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "配置接口",
            color = Color(0xFF111827),
            fontSize = 28.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "填写 Base URL 和 API Key，之后也可以在设置里修改。",
            color = Color(0xFF6B7280),
            lineHeight = 22.sp
        )
        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            label = { Text("Base URL") },
            placeholder = { Text("例如：https://你的服务地址/v1") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
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
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun OobeReadyPage(canSave: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(82.dp)
                .clip(CircleShape)
                .background(if (canSave) successBg else Color(0xFFFFF7ED)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (canSave) "✓" else "!",
                color = if (canSave) successText else Color(0xFFD97706),
                fontSize = 38.sp,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = if (canSave) "准备完成" else "可以稍后配置",
            color = Color(0xFF111827),
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (canSave) {
                "保存后即可开始创作。"
            } else {
                "也可以先进入应用，稍后在设置页补充。"
            },
            color = Color(0xFF6B7280),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}